#!/bin/bash

BASE="http://localhost:8080/api"
PASS='\033[0;32m✓\033[0m'
FAIL='\033[0;31m✗\033[0m'

check() {
  local label=$1
  local expected=$2
  local actual=$3
  if echo "$actual" | grep -q "$expected"; then
    echo -e "$PASS $label"
  else
    echo -e "$FAIL $label"
    echo "   Expected: $expected"
    echo "   Got:      $actual"
  fi
}

echo ""
echo "=============================="
echo "  IssueFlow API Test Suite"
echo "=============================="
echo ""

# 1. Create admin user (idempotent - 201 on first run, 409 on re-runs)
echo "--- Users ---"
R=$(curl -s -X POST "$BASE/users" -H "Content-Type: application/json" \
  -d '{"username":"admin1","email":"admin1@test.com","fullName":"Admin User","password":"pass123","role":"ADMIN"}')
if echo "$R" | grep -q '"id"'; then
  echo -e "$PASS Create admin user (created)"
elif echo "$R" | grep -q '"status":409'; then
  echo -e "$PASS Create admin user (already exists - OK)"
else
  echo -e "$FAIL Create admin user"; echo "   Got: $R"
fi

# 2. Create developer user
R=$(curl -s -X POST "$BASE/users" -H "Content-Type: application/json" \
  -d '{"username":"dev1","email":"dev1@test.com","fullName":"Dev User","password":"pass123","role":"DEVELOPER"}')
if echo "$R" | grep -q '"id"'; then
  echo -e "$PASS Create developer user (created)"
elif echo "$R" | grep -q '"status":409'; then
  echo -e "$PASS Create developer user (already exists - OK)"
else
  echo -e "$FAIL Create developer user"; echo "   Got: $R"
fi

# 3. Login with wrong credentials → 401
echo ""
echo "--- Auth ---"
R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"WRONG"}')
check "Login with wrong password → 401" '"status":401' "$R"

# 4. Login successfully → get token
R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"pass123"}')
check "Login successfully → get token" '"accessToken"' "$R"
TOKEN=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

AUTH="Authorization: Bearer $TOKEN"

# 5. Get current user
R=$(curl -s -X GET "$BASE/auth/me" -H "$AUTH")
check "GET /auth/me → returns current user" '"username":"admin1"' "$R"

# 6. Create project
echo ""
echo "--- Projects ---"
ADMIN_ID=$(curl -s "$BASE/users" -H "$AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
R=$(curl -s -X POST "$BASE/projects" -H "Content-Type: application/json" -H "$AUTH" \
  -d "{\"name\":\"Test Project\",\"description\":\"My test project\",\"ownerId\":$ADMIN_ID}")
check "Create project" '"id"' "$R"
PROJECT_ID=$(echo "$R" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

# 7. Get project by ID
R=$(curl -s "$BASE/projects/$PROJECT_ID" -H "$AUTH")
check "GET project by ID" '"Test Project"' "$R"

# 8. Create ticket (auto-assign to dev1)
echo ""
echo "--- Tickets ---"
R=$(curl -s -X POST "$BASE/tickets" -H "Content-Type: application/json" -H "$AUTH" \
  -d "{\"title\":\"Fix login bug\",\"description\":\"Login is broken\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"type\":\"BUG\",\"projectId\":$PROJECT_ID}")
check "Create ticket" '"id"' "$R"
TICKET_ID=$(echo "$R" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

# 9. Update ticket status TODO → IN_PROGRESS
R=$(curl -s -X PATCH "$BASE/tickets/$TICKET_ID" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"status":"IN_PROGRESS"}')
check "Transition status TODO → IN_PROGRESS" '"IN_PROGRESS"' "$R"

# 10. Try to go backward IN_PROGRESS → TODO → 400
R=$(curl -s -X PATCH "$BASE/tickets/$TICKET_ID" -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"status":"TODO"}')
check "Backward transition → 400" '"status":400' "$R"

# 11. Add a comment
echo ""
echo "--- Comments ---"
AUTHOR_ID=$(curl -s "$BASE/users" -H "$AUTH" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
R=$(curl -s -X POST "$BASE/tickets/$TICKET_ID/comments" -H "Content-Type: application/json" -H "$AUTH" \
  -d "{\"authorId\":$AUTHOR_ID,\"content\":\"This is a comment @dev1\"}")
check "Add comment with mention" '"content"' "$R"
COMMENT_ID=$(echo "$R" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

# 12. Add dependency (self-dependency → 400)
echo ""
echo "--- Dependencies ---"
R=$(curl -s -X POST "$BASE/tickets/$TICKET_ID/dependencies" -H "Content-Type: application/json" -H "$AUTH" \
  -d "{\"blockedBy\":$TICKET_ID}")
check "Self-dependency → 400" '"status":400' "$R"

# 13. Soft delete ticket
echo ""
echo "--- Soft Delete & Restore ---"
R=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/tickets/$TICKET_ID" -H "$AUTH")
check "Soft delete ticket → 204" "204" "$R"

# 14. Restore ticket
R=$(curl -s -X POST "$BASE/tickets/$TICKET_ID/restore" -H "$AUTH")
check "Restore ticket" '"id"' "$R"

# 15. Not found → 404
echo ""
echo "--- Error Handling ---"
R=$(curl -s "$BASE/tickets/99999" -H "$AUTH")
check "GET non-existent ticket → 404" '"status":404' "$R"

# 16. No token → 401
R=$(curl -s "$BASE/projects" -w "\n%{http_code}" | tail -1)
check "Request without token → 401" "401" "$R"

# 17. Export CSV
echo ""
echo "--- CSV ---"
R=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/tickets/export?projectId=$PROJECT_ID" -H "$AUTH")
check "Export tickets to CSV → 200" "200" "$R"

echo ""
echo "=============================="
echo "  Done"
echo "=============================="
echo ""

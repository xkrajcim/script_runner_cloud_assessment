/*
    Workflow engineer assessment v1
    
    Worfklow - P1: Service Request Fulfilment workflow for Jira Service Management
    Transition - Create Issue
    
    Description:
        Script searches for issues in related project (User Accounts/User Access) that are matching 'User ID' field value from created issue.
        For each found issue:
            - a issue link of type "Blocks" will be created between found and created issue
            - a comment will be added to both issues
            
    
    author: m.krajci
*/

// GET 'User ID' field object
def cfUserID = get("/rest/api/2/field")
        .asObject(List)
        .body
        .find {
                (it as Map).name == 'User ID'
        } as Map

// GET field value from created issue
def userIdValue = issue.fields[cfUserID?.id]

// Search for issues with the same User ID in related project
def currentProject = issue.fields.project.key
def relatedProject = currentProject == "ACCESS" ? "ACCOUNTS" : currentProject == "ACCOUNTS" ? "ACCESS" : null

def query = "project = '${relatedProject}' and type = 'Service Request' and 'User ID' ~ '${userIdValue}'" 
def searchReq = get("/rest/api/2/search")
        .queryString("jql", query)
        .queryString("fields", "User ID")
        .asObject(Map)

assert searchReq.status == 200
Map searchResult = searchReq.body

// Check if some issues have been found
if (searchResult.total < 1 ){
    logger.info("No issues have been found by JQL: ${query}")
    return
}

// Loop through issues and create issue link
searchResult.issues.each{ Map foundIssue ->

    // Define outward and inward issues
    def inwardIssue = currentProject == "ACCOUNTS" ? issue : foundIssue
    def outwardIssue = currentProject == "ACCOUNTS" ? foundIssue : issue

    // Create issue link of type 'Blocks'
    def link = post('/rest/api/2/issueLink')
        .header('Content-Type', 'application/json')
        .body([
        type: [ name: "Blocks" ],
        outwardIssue: [ id: outwardIssue.id ],
        inwardIssue: [ id: inwardIssue.id ]
        ]).asString()
        
    assert link.status == 201
    
    // Add comment on each of linked issues
    def commentResp = post("/rest/api/2/issue/${inwardIssue.key}/comment")
        .header('Content-Type', 'application/json')
        .body([
                body: """
            This request blocks '${outwardIssue.key}' request in User Access project.
            """
        ])
        .asObject(Map)
        
    def commentResp2 = post("/rest/api/2/issue/${outwardIssue.key}/comment")
        .header('Content-Type', 'application/json')
        .body([
                body: """
            This request is blocked by '${inwardIssue.key}' request in User Accounts project.
            """
        ])
        .asObject(Map)
    
}
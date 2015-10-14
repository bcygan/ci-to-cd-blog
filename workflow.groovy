def String jenkinsTestHost = "localhost:8080/"
def String jenkinsProductionHost = "localhost:8081/"
def String pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def String pluginFile = "subversion.hpi"
def String stashName = "plugin"

stage "Build"
node {
    echo "++++++++++ Build - getting source code from ${pluginSource}"
    
    git url:pluginSource
    
    echo "++++++++++ Build - running maven"
    
    def mvnHome = tool 'M3'
    sh "${mvnHome}/bin/mvn -DskipTests=true install" // we will come back to the tests later on 
    
    echo "++++++++++ Build - stashing plugin file ${pluginFile}"
    
    stash name: stashName, includes: "target/${pluginFile}" 
}
checkpoint "plugin binary is built"

stage "Integration Tests and Quality Metrics"
parallel
    "Integration Tests": {
        node {
            echo "++++++++++ Integration Tests ++++++++++"

            def mvnHome = tool 'M3'
            sh "${mvnHome}/bin/mvn integration-test"  
            }
        },
    "Quality Metrics": {
        node {
            echo "++++++++++ Quality Metrics ++++++++++"
            
            def mvnHome = tool 'M3'
            sh "${mvnHome}/bin/mvn sonar:sonar"  
            }
        }

// check that the clients still can work with the host
// here we limit concurrency to 2 because we just have 2 slave nodes
stage name: "Load Tests", concurrency: 2 
    parallel 
    "load test linux #1" : {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    },
    "load test linux #2": {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    },
    "load test linux #3": {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }n
}
checkpoint "all tests are done"    

stage "Deploy to Production"
node("linux") {
    input "All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?"
    uploadPluginAndRestartJenkins ( jenkinsProductionHost, "plugin" )
}

def executeLoadTest ( String jenkinsHost ) {
    echo "++++++++++ executing load test against Jenkins host ${jenkinsHost} ++++++++++"
    
    // do here whatever you like, e.g. Selenium, calling the REST API with curl, ...
}

def uploadPluginAndRestartJenkins ( String jenkinsHost, String stashName ) {
    echo "++++++++++ uploading plugin to ${jenkinsHost} ++++++++++"
    unstash stashName
    // execute whatever mechanism you have for deployment of plugins
    // e.g. 
    // scp ${pluginFile} jenkins@jenkins.local:/var/lib/jenkins/plugins
    // java -jar <some-path>/jenkins-cli.jar -s ${jenkinsHost} safe-restart;
    //
}


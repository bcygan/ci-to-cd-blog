def String jenkinsTestHost = "localhost:8080/"
def String jenkinsProductionHost = "localhost:8081/"
def String pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def String pluginFile = "subversion.hpi"
def String stashName = "plugin"

stage "Build"
node("linux") {
    echo "++++++++++ Build - getting source code from ${pluginSource}"
    
    git url:pluginSource
    
    echo "++++++++++ Build - running maven"
    
    def mvnHome = tool 'M3'
    sh "${mvnHome}/bin/mvn -DskipTests=true install"
    
    echo "++++++++++ Build - stashing plugin file ${pluginFile}"
    
    stash name: stashName, includes: "target/${pluginFile}" 
}
checkpoint "plugin binary is built"

stage "Integration Test"
node("linux") {
    echo "++++++++++ Integration Test - unstashing plugin file ${pluginFile}"

    uploadPluginAndRestartJenkins(jenkinsTestHost,stashName)
    
    // perform whatever integration tests you defined
}

stage "Load Tests" // check that the clients still can work with the host
    parallel "load test linux #1" : {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    },
    "load test liunx #2": {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    },
    "load test liunx #3": {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    }
checkpoint "all tests are done"    

stage "Deploy to Production"
node("linux") {
    input "All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?"
    uploadPluginAndRestartJenkins ( jenkinsProductionHost, "plugin" )
}

def executeLoadTest ( String jenkinsHost ) {
    echo "++++++++++ executing load test against Jenkins host " + jenkinsHost
    
    // do here whatever you like, e.g. Selenium, calling the REST API with curl, ...
}

def uploadPluginAndRestartJenkins ( String jenkinsHost, String stashName ) {
    echo "++++++++++ uploading plugin to ${jenkinsHost}"
    unstash stashName
    // execute whatever mechanism you have for deployment of plugins
    // e.g. 
    // scp ${pluginFile} jenkins@jenkins.local:/var/lib/jenkins/plugins
    // java -jar <some-path>/jenkins-cli.jar -s ${jenkinsHost} safe-restart;
    //
}


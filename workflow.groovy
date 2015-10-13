def String jenkinsTestHost = "localhost:8080/"
def String jenkinsProductionHost = "localhost:8080/"
def String pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def String pluginFile = "target/subversion.hpi"

stage "Build"
node("linux") {
    git url:pluginSource
    def mvnHome = tool 'M3'
    sh "${mvnHome}/bin/mvn -DskipTests=true install"
    stash "${pluginFile}"
}
checkpoint "plugin binary is built"

stage "Integration Test"
node("linux") {
    unstash "${pluginFile}"

    uploadPluginAndRestartJenkins(jenkinsTestHost,pluginFile)
    
    // perform whatever integration tests you defined
}

stage "Load Tests" // check that the clients still can work with the host
    parallel "load test linux" : {
        node("linux") {
            executeLoadTest(jenkinsTestHost)
        }
    },
    "load test windows": {
        node("windows") {
            executeLoadTest(jenkinsTestHost)
        }
    }

stage "Deploy to Production"
node("linux") {
    input "All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?"
    unstash "${pluginFile}"
    uploadPluginAndRestartJenkins ( jenkinsProductionHost, pluginFile )
}

def executeLoadTest ( String jenkinsHost ) {
    echo "executing load test against Jenkins host " + jenkinsHost
}

def uploadPluginAndRestartJenkins ( String jenkinsHost, String pluginFile ) {
    echo "uploading ${pluginFile} to ${jenkinsHost}"
}


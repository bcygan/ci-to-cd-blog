def String jenkinsTestHost = "localhost:8080/"
def String jenkinsProductionHost = "localhost:8081/"
def String pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def String pluginFile = "subversion.hpi"

stage "Build"
node("linux") {
    echo "+++ Build - getting source code from ${pluginSource}"
    
    git url:pluginSource
    
    echo "+++ Build - running maven"
    
    def mvnHome = tool 'M3'
    sh "${mvnHome}/bin/mvn -DskipTests=true install"
    
    echo "+++ Build - stashing plugin file ${pluginFile}"
    
    stash name: "plugin", includes: "target/${pluginFile}" // stash these files in the stash named
    
    echo "+++ Build - stashed plugin file ${pluginFile}"
}
echo "+++ Build done - before checkpoint"
checkpoint "plugin binary is built"
echo "+++ Build done - after checkpoint"

stage "Integration Test"
node("linux") {
    echo "+++ Integration Test - unstashing plugin file ${pluginFile}"
    unstash name: "plugin" // this will unstash all previously stashed files 

    uploadPluginAndRestartJenkins(jenkinsTestHost,pluginFile)
    
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

stage "Deploy to Production"
node("linux") {
    input "All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?"
//    unstash "${pluginFile}"
    uploadPluginAndRestartJenkins ( jenkinsProductionHost, pluginFile )
}

def executeLoadTest ( String jenkinsHost ) {
    echo "+++ executing load test against Jenkins host " + jenkinsHost
}

def uploadPluginAndRestartJenkins ( String jenkinsHost, String pluginFile ) {
    echo "++ uploading ${pluginFile} to ${jenkinsHost}"
    unstash "plugin"
    // execute whatever mechanism you have for deployment of plugins
    // e.g. 
    // scp ${pluginFile} jenkins@jenkins.local:/var/lib/jenkins/plugins
    // java -jar <somee-path>/jenkins-cli.jar -s ${jenkinsHost} safe-restart;
    //
}


def String jenkinsTestHost = "localhost:8080/"
def String jenkinsProductionHost = "localhost:8081/"
def String pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def String pluginFile = "subversion.hpi"
def String stashName = "plugin"

stage "Build"
node ('jdk8') {
    echo "++++++++++ Build - getting source code from ${pluginSource}"
    
    git url:pluginSource
    stash name: stashName+"-sources", excludes: "target/*"
    
    echo "++++++++++ Build - running maven"
    
    runMaven "-DskipTests=true install" // we will come back to the tests later on 
    
    echo "++++++++++ Build - stashing plugin file ${pluginFile}"
    
    // recursively collect all .hpi files into a tar.gz
    sh "find . -path '*/target/*.hpi' |grep -v 'test-classes' >target/plugins.list"
    sh "tar -czvf target/${stashName}.tgz --files-from ./target/plugins.list"
    stash name: stashName, includes: "target/${stashName}.tgz" 
}
// checkpoint "plugin binary is built"

stage "Integration Tests and Quality Metrics"
parallel "Integration Tests": {
    node ('jdk8') {
        echo "++++++++++ Integration Tests ++++++++++"

        unstash stashName+"-sources"
        runMaven "integration-test"  
    }
},
"Quality Metrics": {
    node ('jdk8') {
        echo "++++++++++ Quality Metrics ++++++++++"
            
        unstash stashName+"-sources"
        //runMaven "sonar:sonar"  
    }
}
//checkpoint "integration tests and quality metrics are done"

// check that the clients still can work with the host
// here we limit concurrency to 2 because we just have 2 slave nodes
stage name: "Load Tests", concurrency: 2 
parallel "Load Test #1" : {
    node ('jdk8') {
        executeLoadTest(jenkinsTestHost)
    }
},
"Load Test #2": {
    node ('jdk8') {
        executeLoadTest(jenkinsTestHost)
    }
},
"Load Test #3": {
    node ('jdk8') {
        executeLoadTest(jenkinsTestHost)
    }
}
//checkpoint "all tests are done"    

stage "Deploy to Production"
node ('jdk8') {
    input "All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?"
    uploadPluginAndRestartJenkins ( jenkinsProductionHost, "plugin" )
}

def runMaven ( String parameters ) {
    def mvnHome = tool 'maven-3.3'
    sh "${mvnHome}/bin/mvn "+parameters 
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
    // docker.image("").inside {
    //    
    // }
    //
}


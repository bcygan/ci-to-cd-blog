def jenkinsTestHost = new JenkinsHost ( url : ‘localhost’, port : 8080 )
def pluginSource = "https://github.com/jenkinsci/subversion-plugin"
def pluginFile = “target/subversion.hpi”
def mvnHome = tool 'M3'

stage ‘Build’
node(‘linux’) {
    git url:${pluginSource}
    sh "${mvnHome}/bin/mvn install"
    stash '${pluginFile}'
}
checkpoint ‘plugin binary is built’

stage ‘Integration Test’
node(‘linux’) {
    unstash ‘${pluginFile}’

    jenkinsTestHost.uploadPluginAndRestart(${pluginFile})
    
    // perform whatever integration tests you defined
}

stage 'Load Tests' // check that the clients still can work with the host
parallel 
    'load test linux': {
        node('linux') {
            executeLoadTest(jenkinsTestHost)
        }
    }
    'load test windows': {
        node('windows') {
            executeLoadTest(jenkinsTestHost)
        }
    }

stage 'Deploy to Production'
node(‘linux’) {
    input 'All tests are ok. Shall we continue to deploy into production (This will initiate a Jenkins restart) ?''
    unstash '${pluginFile}''
    def jenkinsProductionHost = new JenkinsHost ( url : 'localhost', port : 8080 )
    jenkinsProductionHost.uploadPluginAndRestartJenkins ( ${pluginFile} )
}

class JenkinsHost { 
    // connnect to a Jenkins running locally
    def url = 'localhost'
    def port = '8080' 
    def path = '' 

    // instead of this, we could also spin up a Docker container
    // def image = docker. image('cloudbees/jenkins-enterprise')

    def updloadPluginAndRestartJenkins (String pluginFile) {
        // TODO upload plugin file and restart Jenkins
    }
}


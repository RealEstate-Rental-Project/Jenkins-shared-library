// vars/runSonarAnalysis.groovy
def call(String projectKey, String type = 'auto') {
    withSonarQubeEnv('SonarQube') {
        if(type == 'maven') {
            // Backend Java
            sh "mvn sonar:sonar -Dsonar.projectKey=${projectKey}"
        } else if(type == 'cli') {
            // Frontend JS/TS
            def scannerHome = tool 'sonar-scanner'
            sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${projectKey}"
        } else {
            // Auto-detect par extension du projet (optionnel)
            if(fileExists('pom.xml')) {
                sh "mvn sonar:sonar -Dsonar.projectKey=${projectKey}"
            } else {
                def scannerHome = tool 'sonar-scanner'
                sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${projectKey}"
            }
        }
    }
}

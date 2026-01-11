def call(String projectKey) {
    // On récupère le chemin du scanner configuré à l'étape 2
    def scannerHome = tool 'sonar-scanner'
    
    // 'SonarQube' doit correspondre au nom défini dans Jenkins > System
    withSonarQubeEnv('SonarQube') {
        sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${projectKey}"
    }
}

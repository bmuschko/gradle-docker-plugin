pipeline {
  agent any
  stages {
    stage('Build') {
      agent any
      steps {
        bat 'gradlew.bat build'
      }
    }
  }
}
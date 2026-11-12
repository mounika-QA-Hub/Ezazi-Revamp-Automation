// eZAZI Android UI tests - Jenkins declarative pipeline
// Agent requirements: JDK 17, Android SDK (ANDROID_HOME), Node + Appium 3 + uiautomator2 driver,
// a connected device or an AVD (set EMULATOR_START=true), Jenkins "Allure" plugin.
// Credentials: a "Username with password" credential with id 'ezazi-qa-user'.
pipeline {
    agent { label 'android' }

    parameters {
        choice(name: 'SUITE', choices: ['smoke', 'testng', 'regression', 'parallel-devices'], description: 'Suite XML')
        choice(name: 'ENV', choices: ['qa', 'dev', 'uat'], description: 'Backend environment')
        choice(name: 'VARIANT', choices: ['variantA', 'variantB'], description: 'App variant')
        string(name: 'THREADS', defaultValue: 'auto', description: "Parallel threads ('auto' = one per device)")
        booleanParam(name: 'EMULATOR_START', defaultValue: false, description: 'Boot the AVD if no device is online')
    }

    options {
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('Framework self-test') {
            steps {
                script { mvn('-B clean test -Dsuite=framework') }
            }
        }

        stage('UI tests') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'ezazi-qa-user',
                        usernameVariable: 'EZAZI_USERNAME', passwordVariable: 'EZAZI_PASSWORD')]) {
                    script {
                        mvn("-B test -Dsuite=${params.SUITE} -Denv=${params.ENV} -Dvariant=${params.VARIANT} " +
                            "-Dparallel.threads=${params.THREADS} -Demulator.start=${params.EMULATOR_START} " +
                            "-Dallure.clean.results=false -Dmaven.test.failure.ignore=true")
                    }
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*.xml'
            allure includeProperties: false, results: [[path: 'target/allure-results']]
            archiveArtifacts allowEmptyArchive: true, artifacts: 'target/logs/**, target/screenshots/**'
        }
    }
}

def mvn(String args) {
    if (isUnix()) {
        sh "chmod +x mvnw && ./mvnw ${args}"
    } else {
        bat "mvnw.cmd ${args}"
    }
}

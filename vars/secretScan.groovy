/**
secretScan

Scans a git repository for secrets using gitleaks https://github.com/zricethezav/gitleaks

secretScan(
    failOnLeak: true | false, // (Optional, Fail the pipeline if a leak is found. defaults to true)
    gitleaksConfigFile: '.gitleaks.toml', // (Optional, custom gitleaks config file)
    verbose: true | false // (Optional, Show verbose output from scan. defaults to true)
)
**/

def call(config=[:]) {
    def failOnLeak = config.get('failOnLeak', true)
    def verbose = config.get('verbose', true)
    def exitCode = null
    
    def gitleaksArgs = "--redact --report reports/secret-scan.json"

    if (config.gitleaksConfigFile) {
        gitleaksArgs += " --config-path ${config.gitleaksConfigFile}"
    }

    if (verbose) {
        gitleaksArgs += " --verbose"
    }

    sh('mkdir -p reports')
    
    docker.image('ghcr.io/base2services/gitleaks:7.5.0').inside() {
        exitCode = sh(script: "gitleaks ${gitleaksArgs}", returnStatus:true)
    }

    archiveArtifacts(artifacts: 'reports/secret-scan.json', allowEmptyArchive: true)

    if (exitCode != 0 && failOnLeak) {
        error("One or more leaks detected by secret scan.")
    } else if (exitCode != 0 && !failOnLeak) {
        echo "WARNING: One or more leaks detected by secret scan but ignoring ..."
    } else {
        echo "No leaks found"
    }
}
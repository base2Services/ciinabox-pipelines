#!groovy
@Library('github.com/base2Services/ciinabox-pipelines@master')

// late binded job parameter
def myJobParameter

// late binding closure, executed once called by orchestration engine
def getCfVersion = {
  node('docker') {
    myJobParameter = 'myJobParameterValue'
  }
}

// configuration for application to build. doing dummy checkout for this purpose
def buildConfigs = [
        ['name': 'ciinabox-jenkins', 'repo': 'ciinabox-jenkins', 'branch': 'master'],
        ['name': 'ciinabox-pipelines', 'repo': 'ciinabox-pipelines', 'branch': 'master'],
        ['name': 'ciinabox-ecs', 'repo': 'ciinabox-ecs', 'branch': 'master'],
        ['name': 'ciinabox-containers', 'repo': 'ciinabox-containers', 'branch': 'master'],
        ['name': 'ciinabox-bakery', 'repo': 'ciinabox-bakery', 'branch': 'master']
]

// configuration of whole pipeline, consisting of
// 1 - Build phase (build artifacts) - can be parallelized
// 2 - AMI Base bake phase
// 3 - Application Bake phase - can be parallelized
// 4 - CloudFormation build. CloudFormation update. Must be serialized

def pipelineConfiguration = [
        'Build'                      : [
                parallel: true,
                jobs    : []
        ],
        // Bake Base AMI
        'Bake-Base'                  : [
                'parallel': true,
                'jobs'    : [
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []]
                ]
        ],
        // Bake Role AMI's
        'Bake-PerRole'               : [
                'parallel': true,
                'jobs'    : [
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []],
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []],
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []],
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []]
                ]
        ],
        //Update CloudFormation and environment
        'CloudFormation-Build-Update': [
                'parallel': false,
                'jobs'    : [
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters': []],
                        ['job': getCfVersion, 'name': 'Collect-CF-Version'],
                        ['job': 'MyFolder/MyJobName', 'wait': true, 'parameters':
                                {
                                  return ["CF_BUILD_NO=$myJobParameter",
                                          "ENVIRONMENT_TYPE=dev",
                                          "ENVIRONMENT_NAME=dev"]
                                }]
                ]
        ]
]


currentBuild.result = "SUCCESS"
orgId = "base2Services"

def getBuildStep(name, repo, branch) {
  return {
    echo "Scheduling $name build..."
    node('docker') {
      git branch: branch, url: "https://github.com/${orgId}/${repo}.git"
    }
  }
}

for (int i = 0; i < buildConfigs.size(); i++) {
  pipelineConfiguration['Build']['jobs'] << [
          'job' : getBuildStep(buildConfigs[i].name, buildConfigs[i].repo, buildConfigs[i].branch),
          'wait': true
  ]
}

//create helper
def orchestration = new com.base2.ciinabox.JobOrchestration()

//kick of pipeline
orchestration.batchRunJobs(pipelineConfiguration)



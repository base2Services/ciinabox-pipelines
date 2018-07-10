@Library('github.com/toshke/ciinabox-pipelines@feature/cloudformation-query') _

pipeline {
  agent any
  parameters {
    string(name: 'SOURCE_BUCKET', defaultValue: 'demo-source.ci.base2.services', description: '')
    string(name: 'AWS_REGION', defaultValue: 'ap-southeast-2', description: '')
  }
  stages {
    stage('prepare') {
      steps {
        script {
          def rand = new Random().nextInt().toString(),
              template = "testCloudFormationTemplate${rand}.yaml",
          // simple cloud formation template outputs value of param1, and created EFS volume
              templateContent = """
AWSTemplateFormatVersion: "2010-09-09"
Description: A sample template
Parameters:
  param1:
    Type: String
Outputs:
  out1:
    Value:
      Ref: param1
  efs:
    Value:
      Ref: MyEFS
Resources:
  MyEFS:
    Type: "AWS::EFS::FileSystem"
"""
          writeFile file: template, text: templateContent
          //upload to s3
          s3(file: template,
                  bucket: params.SOURCE_BUCKET,
                  prefix: 'pipeline_tests',
                  region: params.AWS_REGION
          )

          //store template location to be used in subsequent stages
          def templateLocation = "https://${params.SOURCE_BUCKET}.s3.amazonaws.com/pipeline_tests/${template}"
          writeFile file: 'template_location.txt', text: templateLocation
          stash name: 'template_location', include: 'template_location.txt'
        }
      }
    }

    stage('stack-create') {
      steps {
        script {
          unstash 'template_location'
          def templateLocation = readFile file: 'template_location.txt'
          cloudformation(
                  region: params.AWS_REGION,
                  action: 'create',
                  stackName: 'cloudormation-pipeline-test',
                  templateUrl: templateLocation,
                  parameters: [ param1: 'parameter_value' ]
          )
        }

      }
    }
    stage('stack-query') {
      steps {
        script {
          // query created cloudformation stack for output and element info
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'output',
                  query: 'out1'
          )
          def efsInfo = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'element',
                  query: 'MyEFS'
          ),
              paramValue = 'parameter_value'

          echo "Stack has created EFS with resource id ${efsInfo.PhysicalResourceId}"
          echo "Stack has created with param value ${outValue}"
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }

      }
    }
    stage('stack-update') {
      steps {
        script {
          // update stack parameter, and query output
          // test equality between the two
          def paramValue = new Random().nextInt().toString()
          cloudformation(
                  region: params.AWS_REGION,
                  action: 'update',
                  stackName: 'cloudormation-pipeline-test',
                  parameters: [ param1: paramValue ]
          )
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'output',
                  query: 'out1'
          )
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }
      }
    }

    stage('stack-update-query') {
      steps {
        script {
          // testing stack action and query in same step
          def paramValue = new Random().nextInt().toString()
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  action: 'update',
                  stackName: 'cloudormation-pipeline-test',
                  parameters: [ param1: paramValue ],
                  queryType: 'output',
                  query: 'out1'
          )
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }
      }
    }
  }
  post {
    always {
      cloudformation(
              region: params.AWS_REGION,
              action: 'delete',
              stackName: 'cloudormation-pipeline-test'
      )
    }
  }
}

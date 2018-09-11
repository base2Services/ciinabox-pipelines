@Library('ciinabox_tests') _

pipeline {
  agent any
  parameters {
    string(name: 'SOURCE_BUCKET', defaultValue: 'demo-source-ap-southeast-1.ci.base2.services', description: '')
    string(name: 'AWS_REGION', defaultValue: 'ap-southeast-2', description: '')
    string(name: 'SOURCE_BUCKET_REGION', defaultValue: 'ap-southeast-1', description: '')
    string(name: 'TEST_FOR_FAILURE',
            defaultValue: 'false',
            description: 'Set to true to test cloudformation update step failure')
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
                  region: params.SOURCE_BUCKET_REGION
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

    stage('stack-update-remove-parameter') {
      steps {
        script {
          def rand = new Random().nextInt().toString(),
              template = "testCloudFormationTemplate${rand}.yaml",
          // rename param1 as param2
              templateContent = """
AWSTemplateFormatVersion: "2010-09-09"
Description: A sample template
Parameters:
  param2:
    Type: String
Outputs:
  out2:
    Value:
      Ref: param2
  efs:
    Value:
      Ref: MyEFS
Resources:
  MyEFS:
    Type: "AWS::EFS::FileSystem"
"""
          writeFile file: template, text: templateContent
          s3(file: template,
                  bucket: params.SOURCE_BUCKET,
                  prefix: 'pipeline_tests',
                  region: params.SOURCE_BUCKET_REGION
          )
          // update stack parameter, and query output
          // test equality between the two
          def paramValue = new Random().nextInt().toString()
          cloudformation(
                  region: params.AWS_REGION,
                  action: 'update',
                  stackName: 'cloudormation-pipeline-test',
                  // testing format s3.amazonaws.com/bucket/key
                  templateUrl: "https://s3.amazonaws.com/${params.SOURCE_BUCKET}/pipeline_tests/${template}",
                  parameters: [ param2: paramValue ]
          )
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'output',
                  query: 'out2'
          )
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }
      }
    }
    stage('stack-update-another-s3-format-test') {
      steps {
        script {
          def rand = new Random().nextInt().toString(),
              template = "testCloudFormationTemplate${rand}.yaml",
          // rename param1 as param2
              templateContent = """
AWSTemplateFormatVersion: "2010-09-09"
Description: A sample template
Parameters:
  param2:
    Type: String
Outputs:
  out2:
    Value:
      Ref: param2
  addAnotherOut:
    Value:
      Ref: param2
  efs:
    Value:
      Ref: MyEFS
Resources:
  MyEFS:
    Type: "AWS::EFS::FileSystem"
"""
          writeFile file: template, text: templateContent
          s3(file: template,
                  bucket: params.SOURCE_BUCKET,
                  prefix: 'pipeline_tests',
                  region: params.SOURCE_BUCKET_REGION
          )
          // update stack parameter, and query output
          // test equality between the two
          def paramValue = new Random().nextInt().toString()
          cloudformation(
                  region: params.AWS_REGION,
                  action: 'update',
                  stackName: 'cloudormation-pipeline-test',
                  // testing format s3-region.amazonaws.com/bucket/key
                  templateUrl: "https://s3-${params.SOURCE_BUCKET_REGION}.amazonaws.com/${params.SOURCE_BUCKET}/pipeline_tests/${template}",
                  parameters: [ param2: paramValue ]
          )
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'output',
                  query: 'out2'
          )
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }
      }
    }

    stage('stack-update-remove-parameter-json') {
      steps {
        script {
          def rand = new Random().nextInt().toString(),
              template = "testCloudFormationTemplate${rand}.json",
          // rename param1 as param2
              templateContent = """
{
  "AWSTemplateFormatVersion": "2010-09-09", 
  "Outputs": {
    "out3": {
      "Value": {
        "Ref": "param3"
      }
    }, 
    "efs": {
      "Value": {
        "Ref": "MyEFS"
      }
    }
  }, 
  "Parameters": {
    "param3": {
      "Type": "String"
    }
  }, 
  "Description": "A sample template", 
  "Resources": {
    "MyEFS": {
      "Type": "AWS::EFS::FileSystem"
    }
  }
}
"""
          writeFile file: template, text: templateContent
          s3(file: template,
                  bucket: params.SOURCE_BUCKET,
                  prefix: 'pipeline_tests',
                  region: params.SOURCE_BUCKET_REGION
          )
          // update stack parameter, and query output
          // test equality between the two
          def paramValue = new Random().nextInt().toString()
          cloudformation(
                  region: params.AWS_REGION,
                  action: 'update',
                  stackName: 'cloudormation-pipeline-test',
                  //testing format https://bucket.s3-region.amazonaws.com/key
                  templateUrl: "https://${params.SOURCE_BUCKET}.s3-${params.SOURCE_BUCKET_REGION}.amazonaws.com/pipeline_tests/${template}",
                  parameters: [ param3: paramValue ]
          )
          def outValue = cloudformation(
                  region: params.AWS_REGION,
                  stackName: 'cloudormation-pipeline-test',
                  queryType: 'output',
                  query: 'out3'
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
                  parameters: [ param3: paramValue ],
                  queryType: 'output',
                  query: 'out3'
          )
          if (!"${outValue}".equals("${paramValue}")) {
            throw new GroovyRuntimeException("Stack output = ${outValue} does not match ${paramValue}")
          }
        }
      }
    }

    stage('stack-update-failure-test'){
      when { expression { params.TEST_FOR_FAILURE == 'true' } }
      steps {
        script {
          // upload invalid cloudformation template and expect a failure
          def rand = new Random().nextInt().toString(),
              template = "testCloudFormationTemplate${rand}.yaml",
              templateContent = """
AWSTemplateFormatVersion: "2010-09-09"
Description: A sample template
Parameters:
  param3:
    Type: String
Outputs:
  out3:
    Value:
      Ref: param1
  efs:
    Value:
      Ref: MyEFS
Resources:
  MyEFS:
    Type: "AWS::EFS::FileSystem"
  Instance:
    Type: "AWS::EC2::Instance"
    Properties:
      KeyName: idontexistsoiwillrollbackstack
        """
          writeFile file: template, text: templateContent

          //upload to s3
          s3(file: template,
                  bucket: params.SOURCE_BUCKET,
                  prefix: 'pipeline_tests',
                  region: params.SOURCE_BUCKET_REGION
          )

          //testing format bucket.s3.amazonaws.com/key
          def templateLocation = "https://${params.SOURCE_BUCKET}.s3.amazonaws.com/pipeline_tests/${template}"
          try {
            cloudformation(
                    region: params.AWS_REGION,
                    action: 'update',
                    stackName: 'cloudormation-pipeline-test',
                    templateUrl: templateLocation)
            error 'Stack did not update'
          } catch (java.lang.Exception ex) {
            println "Update failed, as expected"
          }

        }
      }
    }
    stage('test-delete'){
      steps {
        cloudformation(
                region: params.AWS_REGION,
                action: 'delete',
                stackName: 'cloudormation-pipeline-test'
        )
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

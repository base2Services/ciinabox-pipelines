/***********************************
 samPackage DSL

 Packages sam template and pushes artefacts to S3

 example usage
 samPackage(
   region: env.AWS_REGION,
   template: template.yaml,
   source_bucket: source.bucket,
   prefix: cloudformation/${PROJECT}/${BRANCH_NAME}/${BUILD_NUMBER}
 )
 ************************************/

def call(body) {
  def config = body

  def compiled_template = config.template.replace(".yaml", "-compiled.yaml")

  println "Compiling SAM template"

  sh """
  #!/bin/bash
  aws cloudformation package \
    --template-file ${config.template} \
    --s3-bucket ${config.source_bucket} \
    --s3-prefix ${config.prefix} \
    --output-template-file ${compiled_template}
  """

  println("Copying ${compiled_template} to s3://${config.source_bucket}/${config.prefix}")

  sh "aws s3 cp ${compiled_template} s3://${config.source_bucket}/${config.prefix}/${compiled_template}"
}

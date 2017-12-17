/***********************************
 s3 DSL

 writes a file to an S3 bucket

 example usage
 s3
   file: 'myfile.yaml',
   bucket: 'mybucket',
   prefix: 'mydata/',
   region: ap-southeast-2
 )
 ************************************/

 def call(body) {
   def config = body
   def include = config.get('include', '*')
   if(config['path']) {
     sh "aws s3 cp ${config.path} s3://${config.bucket}/${config.prefix}/ --exclude \"*\" --include \"${include}\" --recursive --region ${config.region}"
   } else {
     sh "aws s3 cp ${config.file} s3://${config.bucket}/${config.prefix}/${config.file} --region ${config.region}"
   }
 }

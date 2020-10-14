/************************************
washery DSL

from a database backup in RDS, washery removes/anonymises any sensitive data and provide a new copy of that backup 
for non prod environments data refreshes or for local developers through a download from s3.

example usage
washery(
    region: 'ap-southeast-2', // (required)
    accountId: '00000000000', // (optional, provide if assuming role in another account)
    role: 'role-name', // (optional, provide if assuming role in another account)
    sessionDuration: 3600 // (optional, extend the assume role session duration if need be. defaults to 3600)
    snapshotId: 'snapshot-name', // (required, rds snapshot name or arn)
    sqlScript: 'scrubber.sql', // (optional, anonymiser sql script to execute against the dataset)
    scriptBucket: 's3-bucket-name', // (conditional, required if sqlScript is set)
    instanceType: 'instance|cluster', // (required, cluster if using aurora, instance for mysql|postgres|sql-server rds)
    instanceSize: 'db.t3.small', // (optional, overide the default instance sizes set by washery)
    dumpBucket: 's3-bucket-name', // (optional, specify if dumping database to a s3 bucket)
    saveSnapshot: true|false, // (optional, defaults to true. Determines if a snapshot is taken of the scrubbed database)
    databasePasswordArn: 'arn:aws:ssm:us-east-1:00000000000:parameter/my/database/password'
)
************************************/

def call(body) {
    def config = body

    config.saveSnapshot = config.get('saveSnapshot', true)
    def timestamp = new Date().getTime()

    if (!config.saveSnapshot && !config.dumpBucket) {
        error("Either [saveSnapshot: true] or [dumpBucket: 'bucket-name'] must be set or both")
    }

    def s3cmd = ""
    def opts = ""
    opts = "${opts} -s ${config.snapshotId}"
    opts = "${opts} -i ${config.instanceType}"
    opts = "${opts} -p ${config.databasePasswordArn}"
    
    
    if (config.sqlScript && config.scriptBucket) {
        s3cmd = "aws s3 cp ${config.sqlScript} s3://${config.scriptBucket}/washery/script/${config.sqlScript} --region ${config.region}"
        opts = "${opts} -b s3://${config.scriptBucket}/washery/script/${config.sqlScript}"
    }

    if (config.dumpBucket) {
        opts = "${opts} -o s3://${config.dumpBucket}/washery/${config.snapshotId}-${timestamp}"
    }

    if (config.saveSnapshot) {
        opts = "${opts} -S washery-scrubbed-${timestamp}"
    }

    if (config.instanceSize) {
        opt = "${opts} -I ${config.instanceSize}"
    }
    
    def command = "cd /opt/washery && ./main.sh ${opts}"

    echo("running washery with command: ${command}")

    if (config.accountId && config.role) {
        withAWS(region: config.region, role: config.role, roleAccount: config.accountId, duration: config.get('sessionDuration', 3600), roleSessionName: 'washery') {
            if (s3cmd) {
                sh(script: s3cmd, label: 'copy script to s3')
            }
            sh(script: command, label: 'washery')
        }
    } else {
        withAWS(region: config.region) {
            if (s3cmd) {
                sh(script: s3cmd, label: 'copy script to s3')
            }
            sh(script: command, label: 'washery')
        }   
    }

    echo("washery proccess completed")

    if (config.saveSnapshot) {
        env['WASHERY_FINAL_SNAPSHOT'] = "washery-scrubbed-${timestamp}"
    }

    if (config.dumpBucket) {
        env['WASHERY_S3_DUMP'] = "s3://${config.dumpBucke}/washery/${config.snapshotId}-${timestamp}"
    }
}
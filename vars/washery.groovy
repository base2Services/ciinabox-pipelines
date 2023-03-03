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
    dumpBucketPrefix: 'bucket-prefix', // (optional, specify if dumping database to a s3 bucket and you want to add a prefix/folder before the dump prefix/folder)
    saveSnapshot: true|false, // (optional, defaults to true. Determines if a snapshot is taken of the scrubbed database)
    containerImage: 'ghcr.io/base2services/washery:v2', // (optional, the docker image to run in fargate, defaults to ghcr.io/base2services/washery:v2)
    databases: ['mydb', 'anotherdb'] // (optional list of databases to dump, defaults to all databases)
    taskCPU: '1024' // // (optional, provide if overidding default task cpu value)
    taskMemory: '1024' // // (optional, provide if overidding default task memory value)
    resetUserPasswordParameter: '/path/password' // // (optional, path to user password SSM parameter)
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
    
    if (config.sqlScript && config.scriptBucket) {
        s3cmd = "aws s3 cp ${config.sqlScript} s3://${config.scriptBucket}/washery/script/${config.sqlScript} --region ${config.region}"
        opts = "${opts} -b s3://${config.scriptBucket}/washery/script/${config.sqlScript}"
    }

    if (config.dumpBucket) {
        // Automated snapshots have a prefix of "rds:" which breaks the pathing of S3, so we need to remove it if it exists e.g. rds:prod-mysql-instance-2023-02-20-11-01
        if (config.snapshotId.contains("rds:")) {
            snapshotIdPath = config.snapshotId.minus("rds:")
        } else {
            snapshotIdPath = config.snapshotId
        }
        if (config.dumpBucketPrefix) {                
            opts = "${opts} -o s3://${config.dumpBucket}/washery/${config.dumpBucketPrefix}/${snapshotIdPath}-${timestamp}"
        } else {
            opts = "${opts} -o s3://${config.dumpBucket}/washery/${snapshotIdPath}-${timestamp}"
        }
    }

    if (config.saveSnapshot) {
        opts = "${opts} -S washery-scrubbed-${timestamp}"
    }
    
    if (config.taskCPU) {
        opts = "${opts} -p ${config.taskCPU}"
    }
    if (config.taskMemory) {
        opts = "${opts} -N ${config.taskMemory}"
    }
    if (config.resetUserPasswordParameter) {
        opts = "${opts} -P ${config.resetUserPasswordParameter}"
    }

    if (config.instanceSize) {
        opts = "${opts} -I ${config.instanceSize}"
    }

    if (config.accountId && config.role) {
        opts = "${opts} -a ${config.accountId}"
        opts = "${opts} -R ${config.role}"
    }

    if (config.databases) {
        opts = "${opts} -d ${config.databases.join(",")}"
    }

    if (config.containerImage) {
        opts = "${opts} -c ${config.containerImage}"
    }
        
    def command = "cd /opt/washery && ./main.sh ${opts}"

    if (s3cmd) {
        echo("copying the sql script to s3 bucket ${config.scriptBucket}")
        if (config.accountId && config.role) {
            withAWS(region: config.region, role: config.role, roleAccount: config.accountId, duration: config.get('sessionDuration', 900), roleSessionName: 'washery') {
                sh(script: s3cmd, label: 'copy script to s3')
            }
        } else {
            withAWS(region: config.region) {
                sh(script: s3cmd, label: 'copy script to s3')
            }
        }
    }

    echo("running washery with command: ${command}")

    withAWS(region: config.region) {
        sh(script: command, label: 'washery')
    } 

    echo("washery proccess completed")

    if (config.saveSnapshot) {
        env['WASHERY_FINAL_SNAPSHOT'] = "washery-scrubbed-${timestamp}"
    }

    if (config.dumpBucket) {
        env['WASHERY_S3_DUMP'] = "s3://${config.dumpBucket}/washery/${config.snapshotId}-${timestamp}"
    }
}
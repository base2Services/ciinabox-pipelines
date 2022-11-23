/***********************************
washerySnapshotRestore DSL

restores a snapshot to a non prod environment from a washery run

example usage
  washerySnapshotRestore(
    accountId: env.ACCOUNT_ID,
    region: env.REGION,
    role: env.ROLE,
    type: 'rds|dbcluster',
    snapshot: env.WASHERY_FINAL_SNAPSHOT, // the snapshot id of the snapshot created by washery
    resetMasterPassword: '/db/password', // rest the master password to the value in this ssm parameter
    stackName: 'dev', // name of the cloudformation stack with the RDS you are restoring
    snapshotParameterName: 'SnapshotID', // name of the RDS snapshot cloudformation parameter
    resourceIdExportName: 'dev-aurora-mysql-cluster-id', // name of the cloudfomation export to retrive the cluster/rds instance id
    autoApproveChangeSet: true|false // automatically approve the cloudformation changeset, defaults to false
  )

************************************/

import com.amazonaws.services.rds.waiters.AmazonRDSWaiters
import com.amazonaws.services.rds.model.ModifyDBClusterRequest
import com.amazonaws.services.rds.model.DescribeDBClustersRequest
import com.amazonaws.services.rds.model.ModifyDBInstanceRequest
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest
import com.base2.ciinabox.aws.AwsClientBuilder

def call(body) {
    def config = body

    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        awsAccountId: config.accountId,
        role: config.role
    ])

    if (config.type.toLowerCase() == 'rds') {
        def client = clientBuilder.rds()
        def shapshotArn = "arn:aws:rds:${config.region}:${config.accountId}:snapshot:${config.snapshot}"
        def passwordResetHandler = "handleDbClusterPasswordReset"
    } else if (config.type.toLowerCase() == 'dbcluster') {
        def client = clientBuilder.rds()
        def shapshotArn = "arn:aws:rds:${config.region}:${config.accountId}:cluster-snapshot:${config.snapshot}"
        def passwordResetHandler = "handleDbInstancePasswordReset"
    } else {
        throw new GroovyRuntimeException("washerySnapshotRestore() doesn't support type ${config.type}")
    }

    autoApproveChangeSet = config.get('autoApproveChangeSet', false)

    changeSetDeploy(
        description: "Scheduled Washery DB Restore of snapshot ${config.snapshot}",
        region: config.region, 
        stackName: config.stackName,
        awsAccountId: config.accountId,
        role: config.role,
        parameters: [ 
            config.snapshotParameterName : shapshotArn
        ],
        approveChanges: autoApproveChangeSet,
        nestedStacks: true
    )

    if (config.resetMasterPassword && config.resourceIdExportName && config.snapshotParameterName) {
        def resourceId = cloudformation(
            queryType: 'export',
            query: config.resourceIdExportName,
            region: config.region,
            accountId: config.accountId,
            role: config.role
        )
        def password = ssmParameter(
            action: 'get'
            parameter: config.snapshotParameterName,
            region: config.region,
            accountId: config.accountId,
            role: config.role
        )

        println "resetting the ${config.type} master password with the value found in parameter ${config.snapshotParameterName}"

        "$passwordResetHandler"(client, resourceId, password)
    }
}

def handleDbClusterPasswordReset(client, clusterId, password) {
    def modifyDBClusterRequest = new ModifyDBClusterRequest()
            .withDBClusterIdentifier(clusterId)
            .withMasterUserPassword(password)
            .withApplyImmediately(true)
    client.modifyDBCluster(modifyDBClusterRequest)

    println "password has been reset, waiting for the change to be applied to the rds cluster"
    waitTillDbClusterAvailable(client, clusterId)
}

def waitTillDbClusterAvailable(client, clusterId) {
    def waiter = client.waiters().dBClusterAvailable()

    def describeDBClusterRequest = new DescribeDBClustersRequest()
        .withDBClusterIdentifier(clusterId)

    Future future = waiter.runAsync(
        new WaiterParameters<>(describeDBClusterRequest),
        new NoOpWaiterHandler()
    )

    while(!future.isDone()) {
        try {
            println "waiting for rds cluster ${clusterId} to reach the AVAILABLE state"
            Thread.sleep(5 * 1000)
        } catch(InterruptedException ex) {
            println "We seem to be timing out ${ex}...ignoring"
        }
    }
}

def handleDbInstancePasswordReset(client, instanceId, password) {
    def modifyDBInstanceRequest = new ModifyDBInstanceRequest()
        .withDBInstanceIdentifier(instanceId)
        .withApplyImmediately(true)
        .withMasterUserPassword(password)
    client.modifyDBInstance(modifyDBInstanceRequest)

    println "password has been reset, waiting for the change to be applied to the rds instance"
    waitTillDbInstanceAvailable(client, instanceId)
}

def waitTillDbInstanceAvailable(client, instanceId) {
    def waiter = client.waiters().dBInstanceAvailable()

    def describeDBInstancesRequest = new DescribeDBInstancesRequest()
        .withDBInstanceIdentifier(instanceId)

    Future future = waiter.runAsync(
        new WaiterParameters<>(describeDBInstancesRequest),
        new NoOpWaiterHandler()
    )

    while(!future.isDone()) {
        try {
            println "waiting for rds instance ${instanceId} to reach the AVAILABLE state"
            Thread.sleep(5 * 1000)
        } catch(InterruptedException ex) {
            println "We seem to be timing out ${ex}...ignoring"
        }
    }
}
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
    snapshotAccountId: env.DEV_ACCOUNT, // account where the snapshot exists in
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
import java.util.concurrent.Future
import com.amazonaws.waiters.NoOpWaiterHandler
import com.amazonaws.waiters.WaiterParameters

def call(body) {
    def config = body
    def client = null
    def snapshotArn = null
    def passwordResetHandler = null
    def resourceId = null
    def password = null
    def snapshotAccountId = config.get('snapshotAccountId', config.accountId)

    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        awsAccountId: config.accountId,
        role: config.role
    ])

    if (config.type.toLowerCase() == 'rds') {
        snapshotArn = "arn:aws:rds:${config.region}:${snapshotAccountId}:snapshot:${config.snapshot}"
        passwordResetHandler = "handleDbInstancePasswordReset"
    } else if (config.type.toLowerCase() == 'dbcluster') {
        snapshotArn = "arn:aws:rds:${config.region}:${snapshotAccountId}:cluster-snapshot:${config.snapshot}"
        passwordResetHandler = "handleDbClusterPasswordReset"
    } else {
        throw new GroovyRuntimeException("washerySnapshotRestore() doesn't support type ${config.type}")
    }
    
    println "Parameter name: ${config.snapshotParameterName}"
    println "snapshot ARN: ${snapshotArn}"

    autoApproveChangeSet = config.get('autoApproveChangeSet', false)

    //Execute washery restore before rds client is initialised incase duration of changeset exceeds token duration
    changeSetDeploy(
        description: "Scheduled Washery DB Restore of snapshot ${config.snapshot}",
        region: config.region, 
        stackName: config.stackName,
        awsAccountId: config.accountId,
        role: config.role,
        parameters: [ 
            "${config.snapshotParameterName}" : snapshotArn
        ],
        approveChanges: autoApproveChangeSet,
        nestedStacks: true
    )

    client = clientBuilder.rds()

    if (config.resetMasterPassword && config.resourceIdExportName) {
            resourceId = cloudformation(
            queryType: 'export',
            query: config.resourceIdExportName,
            region: config.region,
            accountId: config.accountId,
            role: config.role
        )
        def path = config.resetMasterPassword - config.resetMasterPassword.substring(config.resetMasterPassword.lastIndexOf("/")) 

        ssmParams = ssmParameter(
            action: 'get',
            parameter: path,
            region: config.region,
            accountId: config.accountId,
            role: config.role
        )

        password = getSSMParamValue(ssmParams, config.resetMasterPassword)

        println "resetting the ${config.type} master password with the value found in parameter ${config.resetMasterPassword}"


        println "client: ${client}"
        println "resource: ${resourceId}"

        "$passwordResetHandler"(client, resourceId, password)
    }
}

def getSSMParamValue(ssmParams, name) {
    def value = null
    ssmParams.each { p ->
        if(p.name == name) {
            value = p.value
        }
    }
    return value
}

def handleDbClusterPasswordReset(client, clusterId, password) {
    def modifyDBClusterRequest = new ModifyDBClusterRequest()
            .withDBClusterIdentifier(clusterId)
            .withMasterUserPassword(password)
            .withApplyImmediately(true)
    client.modifyDBCluster(modifyDBClusterRequest)

    println "password has been reset, waiting for the change to be applied to the rds cluster"
    //waitTillDbClusterAvailable(client, clusterId)
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
    //waitTillDbInstanceAvailable(client, instanceId)
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
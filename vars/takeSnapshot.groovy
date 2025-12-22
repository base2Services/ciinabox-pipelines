/***********************************
takeSnapshot DSL
Take RDS/DBCluster/Redshift snapshots
example usage
  takeSnapshot(
    type: 'redshift|rds|dbcluster',
    accountId: env.DEV_ACCOUNT,
    region: env.REGION,
    role: env.ROLE,
    resource: 'my-redshift-cluster',
    envVarName: 'REDSHIFT_SNAPSHOT_ID'
  )
************************************/

import com.amazonaws.services.redshift.model.DescribeClusterSnapshotsRequest
import com.amazonaws.services.redshift.model.CreateClusterSnapshotRequest
import com.amazonaws.services.redshift.model.SnapshotSortingEntity
import com.amazonaws.services.redshift.model.SortByOrder
import com.amazonaws.services.redshift.model.SnapshotAttributeToSortBy

import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest
import com.amazonaws.services.rds.model.CreateDBSnapshotRequest
import com.amazonaws.services.rds.model.CreateDBClusterSnapshotRequest

import com.base2.ciinabox.aws.AwsClientBuilder

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.amazonaws.services.rds.waiters.AmazonRDSWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.WaiterUnrecoverableException
import com.amazonaws.waiters.NoOpWaiterHandler
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.rds.AmazonRDSClientBuilder

def call(body) {
  def config = body

  if(!(config.type)){
    error("type must be specified for takeSnapshot()")
  }

  LocalDateTime localDate = LocalDateTime.now()
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
  String formattedString = localDate.format(formatter)
  snapshot_identifier = "${config.resource}-ondemand-${formattedString}"

  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('accountId', null),
    role: config.get('role', null)
  ])

  if(config.type.toLowerCase() == 'redshift') {
    def client = clientBuilder.redshift()
    handleRedshift(client, config)
  } else if (config.type.toLowerCase() == 'rds') {
    def client = clientBuilder.rds()
    handleRds(client, config)
  } else if (config.type.toLowerCase() == 'dbcluster') {
    def client = clientBuilder.rds()
    handleDBCluster(client, config)
  } else {
    error("takeSnapshot() doesn't support taking a snapshot of type ${config.type}")
  }

}

@NonCPS
def handleDBCluster(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')

  def create_request = new CreateDBClusterSnapshotRequest().withDBClusterIdentifier(config.resource).withDBClusterSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createDBClusterSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeDBClusterSnapshotsRequest()
      .withDBClusterSnapshotIdentifier(snapshot_identifier)  

    def snapshotsResult = client.describeDBClusterSnapshots(describe_request)
    def snapshots = snapshotsResult.getDBClusterSnapshots()

    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getDBClusterSnapshotIdentifier()
        env["${outputName}_ARN"] = snapshots.get(0).getSourceDBClusterSnapshotArn()
        echo("DBCluster snapshot for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      } 
    } catch(InterruptedException ex) {
        // suppress and continue
    }
  }  
}

def wait(clientBuilder, snapshotIdentifier, config) {
  def rdsclient = clientBuilder.rds()
  def waiter = rdsclient.waiters().dBSnapshotAvailable()
  def count = 0

  try {
    def future = waiter.runAsync(
      new WaiterParameters<>(new DescribeDBSnapshotsRequest().withDBSnapshotIdentifier(snapshotIdentifier)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "Waiting for snapshot to become available ..."
        Thread.sleep(10000)
        count++
        // Initialise new client and waiter if count exceeds set timeout value
        if (count > 300) { //3000 seconds = 50 minutes, thread sleep is 10 secs so 300 iterations
          rdsclient = updateClient(clientBuilder, rdsclient, config.region) 
          waiter = updateWaiter(rdsclient)
          future = waiter.runAsync(
             new WaiterParameters<>(new DescribeDBSnapshotsRequest().withDBSnapshotIdentifier(snapshotIdentifier)),
             new NoOpWaiterHandler()
          )
          count = 0
        }

      } catch(InterruptedException ex) {
          // suppress and continue
      }
    }
  } catch(Exception ex) {
    rdsclient = null
    echo "Take snapshot failed with error ${ex.getMessage()}"
    return false
  }  
  return true
}

def updateClient(clientBuilder, rdsclient, region){
  echo "Updating Client"
  def cb = new AmazonRDSClientBuilder().standard()
    .withClientConfiguration(clientBuilder.config())
  if (region) {
    cb.withRegion(region)
  }
  def creds =  clientBuilder.getNewCreds()
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

def updateWaiter(rdsclient){
  echo "Updating Waiter"
  def waiter = rdsclient.waiters().dBSnapshotAvailable()
  echo "Created new waiter - ${waiter}"
  return waiter
}

@NonCPS
def handleRds(client, config) {
  def clientBuilder = new AwsClientBuilder([
    region: config.region,
    awsAccountId: config.get('accountId'),
    role: config.get('role'),
    maxErrorRetry: config.get('maxErrorRetry', 3),
    env: env,
    duration: config.get('duration', 3600)])
  
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')

  def create_request = new CreateDBSnapshotRequest().withDBInstanceIdentifier(config.resource).withDBSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createDBSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  def success = wait(clientBuilder, snapshot_identifier, config)
  // have a go at this later
  /*if (!success) {
    rdsclient = clientBuilder.rds()
    def events = new CloudformationStackEvents(rdsclient, config.region, stackName)
    echo events.getFailedEventsTable()
    events = null
    rdsclient = null
    error "${stackName} changeset ${changeSetName} failed to execute."
  }*/
  
  rdsclient = null
  clientBuilder = null
  echo "Snapshot ${snapshot_identifier} available"

  /*String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeDBSnapshotsRequest()
      .withDBSnapshotIdentifier(snapshot_identifier)
    
    def snapshotsResult = client.describeDBSnapshots(describe_request)
    def snapshots = snapshotsResult.getDBSnapshots()

    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getDBSnapshotIdentifier()
        env["${outputName}_ARN"] = snapshots.get(0).getDBSnapshotArn()
        echo("RDS snapshot created for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      }
    } catch(InterruptedException ex) {
      // suppress and continue
    }
  }*/
}

@NonCPS
def handleRedshift(client, config) {
  def outputName = config.get('envVarName', 'SNAPSHOT_ID')

  def create_request = new CreateClusterSnapshotRequest().withClusterIdentifier(config.resource).withSnapshotIdentifier(snapshot_identifier)
  def create_snapshot_result = client.createClusterSnapshot(create_request)
  echo("Snapshot ${snapshot_identifier} created")

  String snapshot_status = ""
  while(snapshot_status != "available") {
    def describe_request = new DescribeClusterSnapshotsRequest()
      .withSnapshotIdentifier(snapshot_identifier)

    def snapshotsResult = client.describeClusterSnapshots(describe_request)
    def snapshots = snapshotsResult.getSnapshots()
    
    if(snapshots.size() > 0) {
      snapshot_status = snapshots.get(0).getStatus()
      echo("Snapshot is ${snapshot_status}")
    } else {
      error("Unable to find ${snapshot_identifier}")
      break
    }
    try {
      if(snapshot_status == "available") {
        env[outputName] = snapshots.get(0).getSnapshotIdentifier()
        env["${outputName}_OWNER"] = snapshots.get(0).getOwnerAccount()
        env["${outputName}_CLUSTER_ID"] = snapshots.get(0).getClusterIdentifier()
        echo("Redshift snapshot for ${config.resource} created on ${snapshots.get(0).getSnapshotCreateTime().format('d/M/yyyy HH:mm:ss')} is available")
      } else {
        Thread.sleep(10000)
      }
    } catch(InterruptedException ex) {
      // suppress and continue
    }
  }
}
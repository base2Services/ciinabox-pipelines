/***********************************
 ecr DSL
 Triggers, Waits and displays results of a ECR image scan
 example usage
 ecrScan(
   accountId: '1234567890',
   region: 'ap-southeast-2',
   image: 'myrepo/image',
   tag: 'latest',
   trigger: true | false,
   failOn: 'INFORMATIONAL'|'LOW'|'MEDIUM'|'HIGH'|'CRITICAL'|'UNDEFINED'
 )
 ************************************/

@Grab(group='com.jakewharton.fliptables', module='fliptables', version='1.1.0')

import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.DescribeImageScanFindingsRequest
import com.amazonaws.services.ecr.model.ImageIdentifier
import com.amazonaws.services.ecr.model.StartImageScanRequest
import com.amazonaws.services.ecr.waiters.AmazonECRWaiters
import com.amazonaws.waiters.WaiterParameters
import com.amazonaws.waiters.NoOpWaiterHandler
import com.jakewharton.fliptables.FlipTable

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  
  if (config.trigger) {
    triggerScan(ecr,config)
  }
  
  waitForEcrScanResults(ecr,config)
  def results = getScanResults(ecr,config)
  displayEcrScanResults(results)
  failOnSeverity(results,config)
}

@NonCPS
def failOnSeverity(results,config) {
  def severityCount = results.getImageScanFindings().getFindingSeverityCounts()
  def failOn = config.get('failOn','CRITICAL')

  switch(failOn.toUpperCase()) {
    case 'UNDEFINED':
      severityOrder = ['UNDEFINED','INFORMATIONAL','LOW','MEDIUM','HIGH','CRITICAL']
      break
    case ['INFORMATIONAL','INFO']:
      severityOrder = ['INFORMATIONAL','LOW','MEDIUM','HIGH','CRITICAL']
      break
    case 'LOW':
      severityOrder = ['LOW','MEDIUM','HIGH','CRITICAL']
      break
    case ['MEDIUM','MED']:
      severityOrder = ['MEDIUM','HIGH','CRITICAL']
      break
    case 'HIGH':
      severityOrder = ['HIGH','CRITICAL']
      break
    case ['CRITICAL','CRIT']:
      severityOrder = ['CRITICAL']
      break
    default:
      severityOrder = []
  }

  severityOrder.each { severity ->
    if (severityCount.containsKey(severity)) {
      error("Failed ecrScan due to one or more severity findings of ${severityOrder.join(' | ')}\nSummary: ${severityCount}")
    }
  }
}

@NonCPS
def displayEcrScanResults(results) {
  def findings = results.getImageScanFindings().getFindings()
  String[] headers = ['Severity', 'Name', 'Package', 'Version']
  ArrayList data = []
  
  if (findings) {
    findings.each {
      data.add([it.severity, it.name, it.attributes[1].value, it.attributes[0].value])
    }
    println FlipTable.of(headers, data as String[][]).toString()
  } else {
    println "0 findings from image scan"
  }
}

@NonCPS
def waitForEcrScanResults(ecr,config) {
  def waiter = ecr.waiters().imageScanComplete()
  def imageId = new ImageIdentifier().withImageTag(config.tag)
  def waitParameters = new DescribeImageScanFindingsRequest()
      .withRepositoryName(config.image)
      .withRegistryId(config.accountId)
      .withImageId(imageId)

  def future = waiter.runAsync(
    new WaiterParameters<>(waitParameters),
    new NoOpWaiterHandler()
  )

  while(!future.isDone()) {
    try {
      echo "waiting for ecr image scan to complete"
      Thread.sleep(5000)
    } catch(InterruptedException ex) {
        echo "We seem to be timing out ${ex}...ignoring"
    }
  }
}

@NonCPS
def getScanResults(ecr,config) {
  def imageId = new ImageIdentifier().withImageTag(config.tag)
  def request = new DescribeImageScanFindingsRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageId(imageId) 
  def result = ecr.describeImageScanFindings(request)
  return result
}

@NonCPS
def triggerScan(ecr,config) {
  def imageId = new ImageIdentifier().withImageTag(config.tag)
  ecr.startImageScan(new StartImageScanRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withImageId(imageId) 
  )
}

@NonCPS
def setupClient(region) {
  return AmazonECRClientBuilder.standard()
    .withRegion(region)
    .build()
}
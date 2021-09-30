/***********************************
 route53 DSL

 manage AWS Route 53 records

 example usage
 route53(
 action: 'propagateNs'
 srcZone: 'app1.subdomain.example.com'
 dstZone: 'example.com',
 srcZoneAccount: '123456789012',
 srcZoneRole: 'MyRoute53AccessRoleInParentZoneOptionalParam',
 srcZoneRoleExternalId: 'optional-external-id-for-route53-zone-role',
 srcRegion: 'us-east-1' # defaults to us-east-1
 dstZoneAccount: '123456789012',
 dstZoneRole: 'MyRoute53AccessRoleInParentZoneOptionalParam',
 dstZoneRoleExternalId: 'optional-external-id-for-route53-zone-role',
 dstRegion: 'us-east-1  # defaults to us-east-1,
 TTL: 300 # defaults to 5 minutes
 )
 ************************************/

import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.auth.*
import com.amazonaws.services.route53.*
import com.amazonaws.services.route53.model.*

def call(body) {
  def config = body
  def action = config.action
  if (config?.srcRegion == null){
    config.srcRegion = 'us-east-1'
  }
  if (config?.dstRegion == null){
    config.dstRegion = 'us-east-1'
  }
  echo "route53: Executing action ${action} with config: ${config}"
  switch (action) {
    case 'propagateNs':
      echo "propagate"
      createRecordsInParentZone(config)
      return true
      break
    case 'upsert':
      manageRecord(config, ChangeAction.UPSERT)
      return true
      break
    case 'delete':
      manageRecord(config, ChangeAction.DELETE)
      return true
      break
    default:
      error "Route53 step does not support action '${action}'"
      return false
  }
  return false
}

@NonCPS 
def manageRecord(config, action) {
  def dstCredentials = awsCredsProvider(accountId: config?.dstZoneAccount,
          region: config?.dstRegion,
          role: config?.dstZoneRole,
          externalId: config?.dstZoneRoleExternalId
  )
  def dstClientBuilder = AmazonRoute53ClientBuilder.standard().withRegion(config?.dstRegion)
  if (dstCredentials != null) dstClientBuilder = dstClientBuilder.withCredentials(dstCredentials)
  def dstClient = dstClientBuilder.build()
  def dstZone = getZoneByName(dstClient, config.dstZone)
  if (dstZone == null) {
      error "Couldn't find destination hosted zone ${config.dstZone}"
  }

  def request = new ChangeResourceRecordSetsRequest()
    .withHostedZoneId(dstZone.id.replace('/hostedzone/',''))
    .withChangeBatch(
      new ChangeBatch().withChanges(
        new Change()
          .withAction(action)
          .withResourceRecordSet(
            new ResourceRecordSet()
              .withName(config.dstRecord)
              .withType(config.dstRecordType)
              .withTTL(config?.TTL != null ? config.TTL : 60L)
              .withResourceRecords(new ResourceRecord().withValue(config.dstTargetRecord))
          )
      )
    );
  dstClient.changeResourceRecordSets(request)
}

@NonCPS
def createRecordsInParentZone(config) {
  script {
    def srcCredentials = awsCredsProvider(accountId: config?.srcZoneAccount,
            region: config?.srcRegion,
            role: config?.srcZoneRole,
            externalId: config?.srcZoneRoleExternalId
    )
    def dstCredentials = awsCredsProvider(accountId: config?.dstZoneAccount,
            region: config?.dstRegion,
            role: config?.dstZoneRole,
            externalId: config?.dstZoneRoleExternalId
    )
    def srcClientBuilder = AmazonRoute53ClientBuilder.standard()
            .withRegion(config?.srcRegion),
        dstClientBuilder = AmazonRoute53ClientBuilder.standard()
                .withRegion(config?.dstRegion)



    if (srcCredentials != null) srcClientBuilder = srcClientBuilder.withCredentials(srcCredentials)
    if (dstCredentials != null) dstClientBuilder = dstClientBuilder.withCredentials(dstCredentials)


    def srcClient = srcClientBuilder.build(),
        dstClient = dstClientBuilder.build()


    def srcZone = getZoneByName(srcClient, config.srcZone),
        dstZone = getZoneByName(dstClient, config.dstZone)

    if (srcZone == null) {
      error "Couldn't find source hosted zone ${config.srcZone}"
    }

    if (dstZone == null) {
      error "Couldn't find destination hosted zone ${config.dstZone}"
    }

    echo "Using ${srcZone.id}"

    def nsRecord = getNsRecord(srcClient, srcZone.id)
    nsRecord.setTTL(config?.TTL != null ? config.TTL : 300)

    println "Source record set to be delegated: ${nsRecord}"
    println "Delegation zone Id: ${dstZone.id}"

    def delegateRequest = new ChangeResourceRecordSetsRequest()
            .withHostedZoneId(dstZone.id.replace('/hostedzone/',''))
            .withChangeBatch(new ChangeBatch([
            new Change(
                    ChangeAction.UPSERT,
                    nsRecord
            )
    ]))

    dstClient.changeResourceRecordSets(delegateRequest)

  }

}

@NonCPS
def getNsRecord(r53client, zoneId) {
  def recordSets = r53client.listResourceRecordSets(new ListResourceRecordSetsRequest()
          .withHostedZoneId(zoneId.replace('/hostedzone/',''))
  ).resourceRecordSets

  for (int i = 0; i < recordSets.size(); i++) {
    if (recordSets[i].type == 'NS') {
      return recordSets[i]
    }
  }
}


@NonCPS
def getZoneByName(r53client, zoneName) {
  def zones = r53client.listHostedZonesByName(new ListHostedZonesByNameRequest()
          .withDNSName(zoneName)
  ).getHostedZones()
  if (zones.size() > 0) {
    return zones[0]
  }
  return null
}

@NonCPS
def awsCredsProvider(body) {
  def config = body
  if (config?.role != null && config?.accountId != null) {
    return new AWSStaticCredentialsProvider(
            getCredentials(config.accountId, config.role, config?.region, config?.externalId)
    )
  } else {
    return InstanceProfileCredentialsProvider.instance
  }

}


@NonCPS
def getCredentials(awsAccountId, roleName, region, roleExternalId) {
  println "Assuming role"
  def stsCreds = assumeRole(awsAccountId, region, roleName, roleExternalId)
  return new BasicSessionCredentials(
          stsCreds.getAccessKeyId(),
          stsCreds.getSecretAccessKey(),
          stsCreds.getSessionToken()
  )

}

@NonCPS
def assumeRole(awsAccountId, region, roleName, roleExternalId) {
  def roleArn = "arn:aws:iam::${awsAccountId}:role/${roleName}"
  def roleSessionName = "ciinabox-pipelines-${awsAccountId}"
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
    sts.setEndpoint("sts.${region}.amazonaws.com")
  }
  def assumeRoleRequest = new AssumeRoleRequest()
          .withRoleArn(roleArn).withDurationSeconds(3600)
          .withRoleSessionName(roleSessionName)
  if (roleExternalId != null) {
    assumeRoleRequest = assumeRoleRequest.withExternalId(roleExternalId)
  }
  def assumeRoleResult = sts.assumeRole(assumeRoleRequest)
  return assumeRoleResult.getCredentials()
}


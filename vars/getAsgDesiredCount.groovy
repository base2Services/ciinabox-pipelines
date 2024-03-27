/***********************************
getAsgDesiredCount Function

Takes in an auto scaling group returns the current desired count.

example usage
  getAsgDesiredCount (
    asg_name: autoscaling group name,
    region: env.AWS_REGION,
    accountId: env.DEV_ACCOUNT_ID,
    role: 'ciinabox',
    default_count: '5'
  )

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest


def call(body) {
    def config = body
    def clientBuilder = new AwsClientBuilder([
        region: config.region,
        awsAccountId: config.accountId,
        role: config.role
    ])  
    def client = clientBuilder.asg()
    def desired_count = getDesiredCount(client, config)
    print(desired_count)
    return desired_count
}


@NonCPS
def getDesiredCount(client, config) {
    def request = DescribeAutoScalingGroupsRequest()
            .withAutoScalingGroupNames(config.asg)

    def response = client.describeAutoScalingGroups(request)
    print(response)
}


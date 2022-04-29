/***********************************
sqsSendMessage DSL

send message to an SQS queue

example usage
  sqsSendMessage(
        region: "ap-southeast-2",
        accountId: "2340824507009",
        queueName: "myqueue",
        role: "myIAMrole",
        message: "myMessage"
  )

************************************/
import com.base2.ciinabox.aws.AwsClientBuilder


def call (config) {

    def clientBuilder = new AwsClientBuilder([region: config.region, awsAccountId: config.accountId, role: config.role])
    def sqsClient = clientBuilder.sqs()
    println("getting queue url from ${config.queueName}")
    def queueUrlResult = sqsClient.getQueueUrl(config.queueName)
    def queueUrl = queueUrlResult.getQueueUrl()
    println("sending message to ${queueUrl}")
    def messageResult = sqsClient.sendMessage(queueUrl, config.message)
    def messageId = messageResult.getMessageId()
    println("send message ${messageId}")

}
/***********************************
putCloudWatchMetric DSL

put metric to cloudwatch metrics 

putCloudWatchMetric(
    namespace: 'Custom/Namespace',
    metricName: 'metric',
    dimensions: ['key':'value'],
    unit: 'None',
    value: 1
)

************************************/

import com.base2.ciinabox.aws.AwsClientBuilder
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest
import com.amazonaws.services.cloudwatch.model.MetricDatum
import com.amazonaws.services.cloudwatch.model.Dimension

def call(config) {
    def clientBuilder = new AwsClientBuilder()
    def cloudwatchClient = clientBuilder.cloudwatch()

    def dimensions = []
    config.dimensions.each { name, value ->
        dimensions << new Dimension().withName(name).withValue(value)
    }

    def metricData = new MetricDatum()
        .withMetricName(config.metricName)
        .withDimensions(dimensions)
        .withUnit(config.unit)
        .withValue(config.value)

    println "pushing ${config.metricName} metrics to cloudwatch logs"
    def request = new PutMetricDataRequest()
        .withNamespace(config.namespace)
        .withMetricData([metricData])
    cloudwatchClient.putMetricData(request)
}
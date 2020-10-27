/***********************************
createEKSIAMAuth DSL step, integrates IAM to EKS RBAC
agent {
  docker {
    image 'ghcr.io/base2services/eksctl'
  }
}

createEKSIAMAuth(
  region: 'ap-southeast-2',
  cluster: 'my-cluster-name',
  accountId: 123456789,
  role: 'roleToAssume',
  identities: [
    [
        arn: "my_role_arn",
        groups: [
        'system:masters',
        'system:kubelet-api-admin'
        ]
    ]
  ]
)

************************************/

def call(body) {
  def config = body

  if (!config.cluster) {
    error('Cluster must be defined')
  }
  if (!config.region) {
    error('Region must be defined')
  }
  if (!config.identities) {
    error('Identities must be defined')
  }
  if (!config.accountId) {
    error('accountId must be defined')
  }
  if (!config.role) {
    error('role must be defined')
  }

  withAWS(role: config.role, roleAccount: config.accountId, roleSessionName: 'eks-apply-auth', region: config.region) {
    sh "aws eks --region ${config.region} update-kubeconfig --name ${config.cluster}"
    config.identities.each { identity ->
      authStatus = sh(
        returnStatus: true,
        script: "eksctl get iamidentitymapping --cluster ${config.cluster} --region ${config.region} --arn ${identity.arn}"
      )
      println("Got auth status as ${authStatus}")
      if (authStatus != 1) {
        println("${identity.arn} already exists within aws-auth config map, skipping...")
        return
      }

      def createCommand = "eksctl create iamidentitymapping  ${config.cluster} --region ${config.region} --arn ${identity.arn}"
      def groups = ""
      identity.groups.each { group ->
        groups += "--group ${group} "
      }
      println("Running command ${createCommand} ${groups}")
      sh "${createCommand} ${groups}"
    }
  }
}



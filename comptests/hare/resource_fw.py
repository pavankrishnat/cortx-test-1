#!/usr/bin/python3

import subprocess
from time import sleep
from typing import NamedTuple
import sys
import json
import argparse

StatRecord = NamedTuple('StatRecord', [('timestamp', str), ('name', str), ('cpu', str), ('mem', str)])

class Statistics:

    def __init__(self):
        from kubernetes import client, config
        config.load_kube_config()
        self.api = client.CustomObjectsApi()
        self.hax_stats=[]
        self.max_cpu_list=[]

    def collect_readings(self, service):
        curr_stats = []
        k8s_pods = self.api.list_cluster_custom_object("metrics.k8s.io", "v1beta1", "pods")

        for stats in k8s_pods['items']:
            # print(stats.keys(), len(stats['containers']),'\n')

            for field in stats['containers']:
                if field['name'] == service:
                    # print(stats["timestamp"])
                    # print(f'{stats["metadata"]["name"]} {field["usage"]}')
                    curr_stats.append(
                        StatRecord(stats["timestamp"],
                        stats["metadata"]["name"],
                        field["usage"]["cpu"],
                        field["usage"]["memory"]))

        self.aggregate_records(curr_stats)
        self.hax_stats.extend(curr_stats)


    def aggregate_records(self, curr_stats):
      if curr_stats:
        max_cpu = max([ int(stat.cpu[:-1]) for stat in curr_stats])
        self.max_cpu_list.append(max_cpu)
      else:
        print('Service is not present')

    def dump_records(self):
        with open('test.txt', 'a') as f:
            f.write(json.dumps(self.hax_stats))

    def dump_aggregated_records(self, file):
      if self.max_cpu_list:
        from datetime import datetime
        now = datetime.now()
        current_time = now.strftime("%H:%M:%S")
        with open(f'{file}.txt', 'a') as f:
            f.write(
                json.dumps({

                     "timestamp": current_time,
                     "res":self.max_cpu_list,
                     "avg_max": sum(self.max_cpu_list) / len(self.max_cpu_list)}))

class MetricsServer:

    @staticmethod
    def config():
        config_file = "sample1.yaml"
        data="""apiVersion: v1
kind: ServiceAccount
metadata:
  labels:
    k8s-app: metrics-server
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  labels:
    k8s-app: metrics-server
    rbac.authorization.k8s.io/aggregate-to-admin: "true"
    rbac.authorization.k8s.io/aggregate-to-edit: "true"
    rbac.authorization.k8s.io/aggregate-to-view: "true"
  name: system:aggregated-metrics-reader
rules:
- apiGroups:
  - metrics.k8s.io
  resources:
  - pods
  - nodes
  verbs:
  - get
  - list
  - watch
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  labels:
    k8s-app: metrics-server
  name: system:metrics-server
rules:
- apiGroups:
  - ""
  resources:
  - nodes/metrics
  verbs:
  - get
- apiGroups:
  - ""
  resources:
  - pods
  - nodes
  verbs:
  - get
  - list
  - watch
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  labels:
    k8s-app: metrics-server
  name: metrics-server-auth-reader
  namespace: kube-system
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: extension-apiserver-authentication-reader
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  labels:
    k8s-app: metrics-server
  name: metrics-server:system:auth-delegator
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: system:auth-delegator
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  labels:
    k8s-app: metrics-server
  name: system:metrics-server
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: system:metrics-server
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: v1
kind: Service
metadata:
  labels:
    k8s-app: metrics-server
  name: metrics-server
  namespace: kube-system
spec:
  ports:
  - name: https
    port: 443
    protocol: TCP
    targetPort: https
  selector:
    k8s-app: metrics-server
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    k8s-app: metrics-server
  name: metrics-server
  namespace: kube-system
spec:
  selector:
    matchLabels:
      k8s-app: metrics-server
  strategy:
    rollingUpdate:
      maxUnavailable: 0
  template:
    metadata:
      labels:
        k8s-app: metrics-server
    spec:
      containers:
      - args:
        - --cert-dir=/tmp
        - --secure-port=4443
        - --kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname
        - --kubelet-use-node-status-port
        - --metric-resolution=15s
        - --kubelet-insecure-tls
        image: k8s.gcr.io/metrics-server/metrics-server:v0.6.1
        imagePullPolicy: IfNotPresent
        livenessProbe:
          failureThreshold: 3
          httpGet:
            path: /livez
            port: https
            scheme: HTTPS
          periodSeconds: 10
        name: metrics-server
        ports:
        - containerPort: 4443
          name: https
          protocol: TCP
        readinessProbe:
          failureThreshold: 3
          httpGet:
            path: /readyz
            port: https
            scheme: HTTPS
          initialDelaySeconds: 20
          periodSeconds: 10
        resources:
          requests:
            cpu: 100m
            memory: 200Mi
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          runAsUser: 1000
        volumeMounts:
        - mountPath: /tmp
          name: tmp-dir
      nodeSelector:
        kubernetes.io/os: linux
      priorityClassName: system-cluster-critical
      serviceAccountName: metrics-server
      volumes:
      - emptyDir: {}
        name: tmp-dir
---
apiVersion: apiregistration.k8s.io/v1
kind: APIService
metadata:
  labels:
    k8s-app: metrics-server
  name: v1beta1.metrics.k8s.io
spec:
  group: metrics.k8s.io
  groupPriorityMinimum: 100
  insecureSkipTLSVerify: true
  service:
    name: metrics-server
    namespace: kube-system
  version: v1beta1
  versionPriority: 100
"""
        with open(config_file, "w") as f:
            f.write(data)
        output = subprocess.run(["kubectl", "apply", "-f", config_file])
        ready=False
        while not ready:
          sleep(5)
          s2='kubectl get pods --namespace kube-system'
          p1 = subprocess.Popen(s2.split(), stdout=subprocess.PIPE)
          p2 = subprocess.Popen(["grep", "metrics"], stdin=p1.stdout, stdout=subprocess.PIPE)
          x=p2.communicate()
          state = x[0].decode().split()[1]
          ready = state=="1/1"
          print("Waiting...")
        print("installing python dependencies")
        output = subprocess.run(["pip3", "install", "kubernetes"], stdout=subprocess.PIPE)
        print("output=", output.returncode)

def parse_opts(argv):
    p = argparse.ArgumentParser(
        description='Calculates the resources comsumed by pods running hax.',
        usage='%(prog)s [OPTION]')

    p.add_argument('--service',
                   '-s',
                   help='service name. - calculates resources for given service. '
                   'List of services- "hax", "consul" '
                   'Default: xortx-hax.',
                   type=str,
                   default='cortx-hax',
                   action='store')
    p.add_argument(
        '--config',
        help='Sets up the metrics server and installs deps.',
        action='store_true')
    p.add_argument(
        '--phase',
        '-p',
        help='Calculate for that phase and creates a file with phase name'
        'Default: default',
        type=str,
        default='default',
        action='store')
    p.add_argument('--time',
                   '-t',
                   help='time interval of recordings at a given phase'
                   ' Default: 5s',
                   type=int,
                   default=5,
                   action='store')
    return p.parse_args(argv)


def main(argv=None):
    opts = parse_opts(argv)
    if opts.config:
      MetricsServer.config()
    stat = Statistics()
    iterations = 10

    for i in range(iterations):
        stat.collect_readings(opts.service)
        sleep(opts.time)

    stat.dump_records()
    stat.dump_aggregated_records(opts.phase)

if __name__ == '__main__':

     sys.exit(main())
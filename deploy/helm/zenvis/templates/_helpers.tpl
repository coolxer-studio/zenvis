{{- define "zenvis.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "zenvis.fullname" -}}
{{- .Release.Name | trunc 45 | trimSuffix "-" -}}
{{- end -}}

{{- define "zenvis.labels" -}}
app.kubernetes.io/name: {{ include "zenvis.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "zenvis.selectorLabels" -}}
app.kubernetes.io/name: {{ include "zenvis.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "zenvis.image" -}}
{{- $version := default .root.Values.global.version .version -}}
{{- printf "%s/%s:%s-%s" .root.Values.global.imageRegistry .repository $version .root.Values.global.arch -}}
{{- end -}}

{{- define "zenvis.storageClass" -}}
{{- if .Values.persistence.storageClass }}
storageClassName: {{ .Values.persistence.storageClass | quote }}
{{- end }}
{{- end -}}

{{- define "zenvis.mysqlHost" -}}
{{- if .Values.embedded.mysql.enabled -}}
{{ include "zenvis.fullname" . }}-mysql
{{- else -}}
{{ required "external.mysql.host is required when embedded MySQL is disabled" .Values.external.mysql.host }}
{{- end -}}
{{- end -}}

{{- define "zenvis.clickhouseHost" -}}
{{- if .Values.embedded.clickhouse.enabled -}}
{{ include "zenvis.fullname" . }}-clickhouse
{{- else -}}
{{ required "external.clickhouse.host is required when embedded ClickHouse is disabled" .Values.external.clickhouse.host }}
{{- end -}}
{{- end -}}

{{- define "zenvis.redisHost" -}}
{{- if .Values.embedded.redis.enabled -}}
{{ include "zenvis.fullname" . }}-redis
{{- else -}}
{{ required "external.redis.host is required when embedded Redis is disabled" .Values.external.redis.host }}
{{- end -}}
{{- end -}}

{{- define "zenvis.redisStackHost" -}}
{{- if .Values.embedded.redisStack.enabled -}}
{{ include "zenvis.fullname" . }}-redis-stack
{{- else -}}
{{ required "external.redisStack.host is required when embedded Redis Stack is disabled" .Values.external.redisStack.host }}
{{- end -}}
{{- end -}}

{{- define "zenvis.kafkaServers" -}}
{{- if .Values.embedded.kafka.enabled -}}
{{ include "zenvis.fullname" . }}-kafka:9092
{{- else -}}
{{ required "external.kafka.bootstrapServers is required when embedded Kafka is disabled" .Values.external.kafka.bootstrapServers }}
{{- end -}}
{{- end -}}

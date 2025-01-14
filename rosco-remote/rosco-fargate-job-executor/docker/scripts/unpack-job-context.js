#!/usr/bin/env node

const fs = require('fs')
const path = require('path')

// noinspection JSFileReferences
const base64EncodedPayload = require('./job-context.json')["base64-encoded-job-context"]

/** @type {JobContext} */
const jobContext = JSON.parse(new Buffer.from(base64EncodedPayload, 'base64').toString())

// Make the config dir, if it
const configDir = jobContext.configDir
if (!fs.existsSync(configDir)) {
    fs.mkdirSync(configDir, { recursive: true })
}

const configMap = jobContext.configMap
Object.keys(configMap).forEach(configurationFileName => {
    const data = configMap[configurationFileName]
    fs.writeFileSync(path.join(configDir, configurationFileName), data)
})

const awsCredentials = jobContext.awsCredentials || {}

const jobScript = `#!/usr/bin/env bash

${Object.keys(awsCredentials).map(envVar => {
    return `export ${envVar}=${awsCredentials[envVar]}`
}).join("\n")}

timeout "${jobContext.commandTimeout}" ${jobContext.jobCommand}  || {
  exitCode=$?
  echo "The Rosco Job exited with code: \${exitCode}"
  exit \${exitCode}
}
`

const scriptPath = path.join(".", 'execute-rosco-job.sh')
fs.writeFileSync(scriptPath, jobScript)
fs.chmodSync(scriptPath, "755");

// "types" for intellisense / IDEs
/**
 * The Job Context Object
 *
 * @interface JobContext
 * @typedef JobContext
 * @type {Object}
 * @property {Object.<string, string>} configMap serialized map<string>
 * @property {string} configDir
 * @property {string} jobCommand
 * @property {string} commandTimeout
 * @property {Object.<string, string>} awsCredentials serialized map<string>
 */
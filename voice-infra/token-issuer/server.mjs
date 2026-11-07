import crypto from 'node:crypto'
import http from 'node:http'
import { AccessToken } from 'livekit-server-sdk'

const required = ['LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET', 'LIVEKIT_PUBLIC_URL', 'VOICE_PLUGIN_SECRET', 'TURN_DOMAIN', 'TURN_SHARED_SECRET']
for (const key of required) if (!process.env[key]) throw new Error(`Missing ${key}`)

const pluginSecret = Buffer.from(process.env.VOICE_PLUGIN_SECRET)
const json = (res, status, data) => {
  res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' })
  res.end(JSON.stringify(data))
}

const makeTurnCredentials = identity => {
  const username = `${Math.floor(Date.now() / 1000) + 600}:${identity}`
  const credential = crypto.createHmac('sha1', process.env.TURN_SHARED_SECRET).update(username).digest('base64')
  const host = process.env.TURN_DOMAIN
  return {
    username,
    credential,
    urls: [`turn:${host}:3478?transport=udp`, `turn:${host}:3478?transport=tcp`],
  }
}

http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/healthz') return json(res, 200, { ok: true })
  if (req.method !== 'POST' || req.url !== '/token') return json(res, 404, { error: 'not found' })
  const suppliedSecret = Buffer.from(String(req.headers['x-voice-plugin-secret'] || ''))
  if (suppliedSecret.length !== pluginSecret.length || !crypto.timingSafeEqual(suppliedSecret, pluginSecret)) {
    return json(res, 401, { error: 'unauthorized' })
  }
  let body = ''
  for await (const chunk of req) body += chunk
  try {
    const { identity, username, server, mode } = JSON.parse(body)
    if (!/^[0-9a-f-]{36}$/i.test(identity) || typeof username !== 'string' || !/^[a-z0-9-]{1,48}$/i.test(server) || !['global', 'around'].includes(mode)) {
      return json(res, 400, { error: 'invalid voice request' })
    }
    const room = `webcake:${server}:${mode}`
    const token = new AccessToken(process.env.LIVEKIT_API_KEY, process.env.LIVEKIT_API_SECRET, {
      identity,
      name: username.slice(0, 32),
      ttl: '10m',
    })
    token.addGrant({ roomJoin: true, room, canPublish: true, canSubscribe: true, canPublishData: false })
    return json(res, 200, {
      url: process.env.LIVEKIT_PUBLIC_URL,
      room,
      token: await token.toJwt(),
      identity,
      iceServers: [makeTurnCredentials(identity)],
    })
  } catch (error) {
    console.error('voice token request failed', error)
    return json(res, 400, { error: 'invalid JSON' })
  }
}).listen(Number(process.env.PORT || 3000), '0.0.0.0', () => console.log('voice token issuer listening'))

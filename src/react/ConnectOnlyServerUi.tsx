import { FormEvent, useState } from 'react'
import { appQueryParams } from '../appParams'
import { ConnectOptions } from '../connect'
import { lastConnectOptions } from '../appStatus'
import PixelartIcon, { pixelartIcons } from './PixelartIcon'
import { useIsModalActive } from './utilsApp'
import Button from './Button'

const VERTICAL_LAYOUT = false

export default () => {
  const { ip, version, proxy, username: configuredUsername, connectText } = appQueryParams
  const isModalActive = useIsModalActive('only-connect-server')
  const [username, setUsername] = useState(configuredUsername || '')
  const [password, setPassword] = useState('')

  if (!isModalActive) return null

  const handleConnect = (event: FormEvent) => {
    event.preventDefault()
    if (!username.trim() || !password) return
    const connectOptions: ConnectOptions = {
      username: username.trim(),
      loginPassword: password,
      server: ip,
      proxy,
      botVersion: version,
    }
    window.dispatchEvent(new CustomEvent('connect', { detail: connectOptions }))
  }

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      width: '100%',
      height: '100%',
      background: 'rgba(0, 0, 0, 0.25)',
      zIndex: 1000,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center'
    }}>
      <form onSubmit={handleConnect} style={{
        display: 'flex',
        gap: '18px',
        alignItems: VERTICAL_LAYOUT ? 'center' : 'stretch',
        background: 'rgba(0, 0, 0, 0.5)',
        padding: '28px',
        borderRadius: '5px',
        flexDirection: VERTICAL_LAYOUT ? 'column' : 'row',
        minWidth: '300px',
      }}>
        <div style={{
          color: 'white',
          fontSize: '13px',
          minWidth: '190px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: '10px' }}>
            <PixelartIcon iconName={pixelartIcons.server} width={16} />
            <span style={{ marginLeft: '8px' }}>{ip}</span>
            {proxy && <span style={{ marginLeft: '8px', color: 'lightgray' }}>({proxy})</span>}
          </div>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <PixelartIcon iconName={pixelartIcons.user} width={16} />
            {version && <span style={{ marginLeft: '8px', color: 'lightgray' }}>({version})</span>}
          </div>
          <label style={{ display: 'block', marginTop: '16px', color: 'lightgray' }}>
            Username
            <input required autoFocus autoComplete='username' value={username} onChange={e => setUsername(e.target.value)} style={inputStyle} />
          </label>
          <label style={{ display: 'block', marginTop: '10px', color: 'lightgray' }}>
            Password
            <input required type='password' autoComplete='current-password' value={password} onChange={e => setPassword(e.target.value)} style={inputStyle} />
          </label>
        </div>
        <Button
          type='submit'
          style={{
            width: 'auto',
            padding: '0 12px',
            transform: 'scale(1.4)',
            transformOrigin: 'center'
          }}
        >
          {connectText || 'Play'}
        </Button>
      </form>
    </div>
  )
}

const inputStyle = {
  display: 'block',
  boxSizing: 'border-box' as const,
  width: '100%',
  marginTop: '5px',
  padding: '8px',
  color: 'white',
  background: 'rgba(0, 0, 0, 0.45)',
  border: '1px solid #777',
  borderRadius: '3px',
}

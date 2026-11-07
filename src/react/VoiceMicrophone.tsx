import { proxy } from 'valtio'

export const voiceChatStatus = proxy({
  active: false,
  muted: true,
  hasInputVoice: false,
  isErrored: false,
  isConnected: false,
  isAlone: false,

  isSharingScreen: false,
  /** The current server advertised voice chat at least once this session. Sticky — never reset to false, so pause-screen controls stay available even after declining or leaving. */
  serverSupportsVoice: false,
  /** Between sending 'enable' and the connection settling (success or failure). Lets buttons show a "Joining…" state and avoid double-submits. */
  isJoining: false,
  mode: 'around' as 'around' | 'global',
  /** Safe-to-display failure detail from the server or LiveKit SDK. */
  lastError: '',
})

window.voiceChatStatus = voiceChatStatus

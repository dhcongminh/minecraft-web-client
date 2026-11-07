import { isCypress } from './standaloneUtils'

// might not resolve at all
export const registerServiceWorker = async () => {
  if (!('serviceWorker' in navigator) || process.env.SINGLE_FILE_BUILD) return
  if (process.env.DISABLE_SERVICE_WORKER) {
    // This deployment is a login gateway, not an offline app. Remove any
    // previously registered worker so it cannot keep serving an old entry page.
    const registrations = await navigator.serviceWorker.getRegistrations()
    await Promise.all(registrations.map(registration => registration.unregister()))
    return
  }
  if (!isCypress() && process.env.NODE_ENV !== 'development') {
    return new Promise<void>(resolve => {
      window.addEventListener('load', async () => {
        await navigator.serviceWorker.register('./service-worker.js').then(registration => {
          console.log('SW registered:', registration)
          resolve()
        }).catch(registrationError => {
          console.log('SW registration failed:', registrationError)
        })
      })
    })
  } else {
    // force unregister service worker in development mode
    const registrations = await navigator.serviceWorker.getRegistrations()
    for (const registration of registrations) {
      await registration.unregister() // eslint-disable-line no-await-in-loop
    }
    if (registrations.length) {
      location.reload()
    }
  }
}

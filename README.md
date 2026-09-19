# Render VPN — importable WireGuard profile

This Android app can import a WireGuard `.conf` file or paste a WireGuard configuration, validate it, and request Android VPN permission through the WireGuard backend.

The repository does NOT contain a VPN server or private key. Keep private keys off GitHub.

The app uses the official WireGuard Android tunnel library. The official project documents Maven Central embedding and its Go userspace backend. 

Build with GitHub Actions:
1. Push to main.
2. Open Actions.
3. Run Build Render VPN APK.
4. Download the RenderVPN-debug artifact.

A real tunnel requires a WireGuard server/profile.

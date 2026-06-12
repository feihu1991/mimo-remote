// cli/src/crypto.ts — Encryption utilities using libsodium

import sodium from 'libsodium-wrappers';

export class Crypto {
  private keypair: { publicKey: Uint8Array; privateKey: Uint8Array } | null = null;
  private initialized = false;

  async init(): Promise<void> {
    await sodium.ready;
    this.keypair = sodium.crypto_box_keypair();
    this.initialized = true;
  }

  getPublicKey(): string {
    if (!this.keypair) throw new Error('Not initialized');
    return sodium.to_base64(this.keypair.publicKey);
  }

  getPrivateKey(): string {
    if (!this.keypair) throw new Error('Not initialized');
    return sodium.to_base64(this.keypair.privateKey);
  }

  getPairingInfo(): { publicKey: string; secretKey: string } {
    if (!this.keypair) throw new Error('Not initialized');
    return {
      publicKey: sodium.to_base64(this.keypair.publicKey),
      secretKey: sodium.to_base64(this.keypair.privateKey),
    };
  }

  generateToken(): string {
    const bytes = sodium.randombytes_buf(32);
    return sodium.to_base64(bytes);
  }

  encrypt(message: string, recipientPublicKey: string): string {
    if (!this.keypair) throw new Error('Not initialized');
    const pubKey = sodium.from_base64(recipientPublicKey);
    const nonce = sodium.randombytes_buf(sodium.crypto_box_NONCEBYTES);
    const ciphertext = sodium.crypto_box_easy(
      sodium.from_string(message),
      nonce,
      pubKey,
      this.keypair.privateKey
    );

    // Prepend nonce to ciphertext
    const combined = new Uint8Array(nonce.length + ciphertext.length);
    combined.set(nonce);
    combined.set(ciphertext, nonce.length);
    return sodium.to_base64(combined);
  }

  decrypt(encrypted: string, senderPublicKey: string): string {
    if (!this.keypair) throw new Error('Not initialized');
    const combined = sodium.from_base64(encrypted);
    const nonce = combined.slice(0, sodium.crypto_box_NONCEBYTES);
    const ciphertext = combined.slice(sodium.crypto_box_NONCEBYTES);
    const pubKey = sodium.from_base64(senderPublicKey);

    const plaintext = sodium.crypto_box_open_easy(
      ciphertext,
      nonce,
      pubKey,
      this.keypair.privateKey
    );
    return sodium.to_string(plaintext);
  }

  // Generate shared secret for session encryption
  computeSharedSecret(peerPublicKey: string): Uint8Array {
    if (!this.keypair) throw new Error('Not initialized');
    const pubKey = sodium.from_base64(peerPublicKey);
    return sodium.crypto_box_beforenm(pubKey, this.keypair.privateKey);
  }
}

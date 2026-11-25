//! Shared Shod link encoding, encryption and construction helpers.

use crate::model;

pub(super) fn url_encode(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for b in input.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char);
            }
            _ => {
                use std::fmt::Write;
                let _ = write!(out, "%{:02X}", b);
            }
        }
    }
    out
}

pub const SHOD_CIPHER_KEY_SEED: &str = "shod-chiper-key";

pub fn encrypt_shod_link(payload: &str) -> String {
    use aws_lc_rs::aead::{AES_256_GCM, Aad, LessSafeKey, Nonce, UnboundKey};
    use base64::Engine;
    use hmac::{Hmac, KeyInit, Mac};
    use sha2::{Digest, Sha256};

    let key_hash = Sha256::digest(SHOD_CIPHER_KEY_SEED.as_bytes());
    let unbound = match UnboundKey::new(&AES_256_GCM, &key_hash) {
        Ok(k) => k,
        Err(_) => {
            let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
            return format!("shod://{b64}");
        }
    };
    let key = LessSafeKey::new(unbound);

    let mut iv = [0u8; 12];
    type HmacSha256 = Hmac<Sha256>;
    if let Ok(mut mac) = HmacSha256::new_from_slice(&key_hash) {
        mac.update(payload.as_bytes());
        let result = mac.finalize().into_bytes();
        iv.copy_from_slice(&result[..12]);
    } else {
        use rand::RngCore;
        rand::rngs::OsRng.fill_bytes(&mut iv);
    }
    let nonce = match Nonce::try_assume_unique_for_key(&iv) {
        Ok(n) => n,
        Err(_) => {
            let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
            return format!("shod://{b64}");
        }
    };

    let mut in_out = payload.as_bytes().to_vec();
    if key
        .seal_in_place_append_tag(nonce, Aad::empty(), &mut in_out)
        .is_err()
    {
        let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
        return format!("shod://{b64}");
    }

    let mut combined = Vec::with_capacity(12 + in_out.len());
    combined.extend_from_slice(&iv);
    combined.extend_from_slice(&in_out);

    let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(&combined);
    format!("shod://{b64}")
}

#[allow(dead_code)]
pub fn decrypt_shod_link(raw_link: &str) -> Option<String> {
    use aws_lc_rs::aead::{AES_256_GCM, Aad, LessSafeKey, Nonce, UnboundKey};
    use base64::Engine;
    use sha2::{Digest, Sha256};

    let payload = raw_link.trim().strip_prefix("shod://")?.trim();
    let clean = payload.replace(['\n', '\r'], "");
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(&clean)
        .or_else(|_| base64::engine::general_purpose::URL_SAFE.decode(&clean))
        .or_else(|_| base64::engine::general_purpose::STANDARD_NO_PAD.decode(&clean))
        .or_else(|_| base64::engine::general_purpose::STANDARD.decode(&clean))
        .ok()?;

    if bytes.len() >= 28 {
        let key_hash = Sha256::digest(SHOD_CIPHER_KEY_SEED.as_bytes());
        if let Ok(unbound) = UnboundKey::new(&AES_256_GCM, &key_hash) {
            let key = LessSafeKey::new(unbound);
            let mut iv = [0u8; 12];
            iv.copy_from_slice(&bytes[..12]);
            if let Ok(nonce) = Nonce::try_assume_unique_for_key(&iv) {
                let mut ciphertext = bytes[12..].to_vec();
                if let Ok(decrypted) = key.open_in_place(nonce, Aad::empty(), &mut ciphertext)
                    && let Ok(s) = String::from_utf8(decrypted.to_vec())
                {
                    return Some(s);
                }
            }
        }
    }

    None
}

pub(super) fn make_client_link(
    host: &str,
    port: u16,
    password: &str,
    vk_hashes: &str,
    vk_token: bool,
    shared_token: &str,
) -> String {
    let mut payload = format!(
        "connect?v=2&host={}&peer={}",
        url_encode(host),
        if port == 0 { 46000 } else { port },
    );
    if !password.is_empty() {
        payload.push_str("&password=");
        payload.push_str(&url_encode(password));
    }
    let hashes: Vec<&str> = vk_hashes
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .collect();
    if !hashes.is_empty() {
        payload.push_str("&hashes=");
        payload.push_str(
            &hashes
                .iter()
                .map(|h| url_encode(h))
                .collect::<Vec<_>>()
                .join("+"),
        );
    } else if vk_token && !shared_token.is_empty() {
        payload.push_str("&token=");
        payload.push_str(&url_encode(shared_token));
    }
    encrypt_shod_link(&payload)
}

#[inline]
pub(super) fn make_main_link(host: &str, port: u16, db: &model::Database) -> String {
    let vk_hashes = if db.main_vk_hashes.is_empty() {
        &db.vk_hashes
    } else {
        &db.main_vk_hashes
    };
    make_client_link(
        host,
        port,
        &db.main_password,
        vk_hashes,
        db.main_vk_token_enabled,
        &db.vk_access_token,
    )
}

#[inline]
pub(super) fn make_entry_link(
    host: &str,
    port: u16,
    password: &str,
    entry: &model::PasswordEntry,
    shared_token: &str,
) -> String {
    make_client_link(
        host,
        port,
        password,
        &entry.vk_hashes,
        entry.vk_token_enabled,
        shared_token,
    )
}

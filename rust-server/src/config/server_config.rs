//! Canonical, single-server network and runtime configuration.
pub const TUN_IFACE: &str = "shod1";
pub const MANAGED_TAG: &str = "Shod_MANAGED";
pub const TPROXY_TAG: &str = "Shod_TPROXY";
pub const TPROXY_TABLE: &str = "30001";
pub const TPROXY_MARK: &str = "0x7531/0x7531";
pub const TUN_GATEWAY: &str = "10.66.67.1";
pub const TUN_SUBNET_CIDR: &str = "10.66.67.0/24";
pub const SUBNET_PREFIX: [u8; 3] = [10, 66, 67];
pub const OWNER_TUNNEL_IP: &str = "10.66.67.2";

// Fixed runtime values for the single supported Shod server.
pub const TPROXY_BASE_PORT: u16 = 10666;
pub const SESSION_COOKIE: &str = "shod_session";
pub const FALLBACK_CLIENT_IP: &str = "10.66.67.3";

export default {
  Encrypt(data: string, key: string): string {
    return data + key;
  },
  Decrypt(data: string, key: string): string {
    return data + key;
  },
  k(key: string): string {
    return key;
  },
};

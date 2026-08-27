import md5 from 'js-md5';
import { getAuthSalt, getAuthToken } from '@u/auth-session';

const getTokenSign = (param: Record<string, unknown> = {}): Record<string, string> => {
  const token = getAuthToken();
  const salt = getAuthSalt();
  const signatureContent = md5(JSON.stringify(param) + token) + new Date().getTime() + salt;
  const clientSign = md5(signatureContent);
  return {
    token,
    clientSign,
    signatureContent,
  };
};

export { getTokenSign };

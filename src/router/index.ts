import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router';
import { hasLoginSession, setAuthToken } from '@u/auth-session';
import layout_blank from '@c/layout/layout-blank.vue';
import layout_header from '@c/layout/layout-header.vue';
import layout_full from '@c/layout/layout-full.vue';

const LOGIN_PATH = '/user/login';
const HOME_PATH = '/dashboard/index';
const PUBLIC_PATHS = new Set([LOGIN_PATH, '/ExceptionPage404', '/ExceptionPage403', '/ExceptionPage500']);

const firstQueryValue = (value: unknown): string => {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
};

const basicRoutes: Array<RouteRecordRaw> = [
  {
    path: `/user`,
    component: layout_blank,
    redirect: `/user/login`,
    children: [
      {
        path: 'login',
        component: () => import('@v/login/index.vue'),
        name: 'login'
      }
    ]
  },
  {
    path: `/dashboard`,
    component: layout_full,
    redirect: `/dashboard/index`,
    children: [
      {
        path: 'index',
        component: () => import('@v/dashboard/index.vue'),
        name: 'dashboard'
      }
    ]
  },
  {
    path: `/retrieval`,
    component: layout_header,
    children: [
      {
        path: 'index',
        component: () => import('@v/retrieval/index.vue'),
        name: 'retrieval'
      }
    ]
  },
  {
    path: `/service`,
    component: layout_blank,
    children: [
      {
        path: 'dih',
        component: () => import('@v/dih/index.vue'),
        name: 'service-dih'
      }
    ]
  },
  {
    path: `/service`,
    component: layout_header,
    children: [
      {
        path: 'low-code-app/:menuParams',
        component: () => import('@v/low-code-app/index.vue'),
        name: 'low-code-app'
      },
      {
        path: 'low-code-page/:menuParams',
        component: () => import('@v/low-code-page/index.vue'),
        name: 'low-code-page'
      },
      {
        path: 'external-app/:menuParams',
        component: () => import('@v/external-app/index.vue'),
        name: 'external-app'
      },
      {
        path: 'html-page/:menuParams',
        component: () => import('@v/html-page/index.vue'),
        name: 'html-page'
      }
    ]
  },
  {
    path: `/plugin/config`,
    component: layout_blank,
    children: [
       {
        path: '::menuParams',
        component: () => import('@v/policy/index.vue'),
        name: 'plugin-config'
      }
    ]
  },
  {
    path: `/policy`,
    component: layout_header,
    children: [
       {
        path: '::menuParams',
        component: () => import('@v/policy/index.vue'),
        name: 'policy-config'
      }
    ]
  },
  {
    path: `/system`,
    component: layout_header,
    children: [
      {
        path: 'about',
        component: () => import('@v/about/index.vue'),
        name: 'system-about'
      }
    ]
  }
];

const routes: Array<RouteRecordRaw> = [
  ...basicRoutes,
  {
    path: '/',
    redirect: '/dashboard/index'
  },
  {
    path: '/ExceptionPage404',
    name: 'ExceptionPage404',
    component: () => import('@v/pages-error/404.vue'),
  },
  {
    path: '/ExceptionPage403',
    name: 'ExceptionPage403',
    component: () => import('@v/pages-error/403.vue'),
  },
  {
    path: '/ExceptionPage500',
    name: 'ExceptionPage500',
    component: () => import('@v/pages-error/500.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/ExceptionPage404',
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

/**
 * description: 路由守卫设置
 */
router.beforeEach(to => {
  const token = firstQueryValue(to.query.token);
  const salt = firstQueryValue(to.query.salt);
  if (token) {
    setAuthToken(token, salt);
  }

  if (to.matched.length === 0) {
    return { path: '/ExceptionPage404', replace: true };
  }

  const isLoggedIn = hasLoginSession();
  if (!isLoggedIn && !PUBLIC_PATHS.has(to.path)) {
    return {
      path: LOGIN_PATH,
      query: { redirect: to.fullPath },
    };
  }

  if (isLoggedIn && to.path === LOGIN_PATH) {
    return HOME_PATH;
  }

  return true;
});

export default router;

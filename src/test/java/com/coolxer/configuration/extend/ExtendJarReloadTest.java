package com.coolxer.configuration.extend;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtendJarReloadTest {

    private static final String FIXTURE_PACKAGE = "dynamic.plugin.reload";

    @Test
    void reloadsPluginBeansWithAReplacementClassLoader() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
            when(handlerMapping.getHandlerMethods()).thenReturn(Map.of());

            Registrar registrar = new Registrar();
            ReflectionTestUtils.setField(registrar, "ctx", context);
            ReflectionTestUtils.setField(registrar, "extendJarHandlerMapping", handlerMapping);
            Cleaner cleaner = new Cleaner();
            ReflectionTestUtils.setField(cleaner, "ctx", context);
            ReflectionTestUtils.setField(cleaner, "extendJarHandlerMapping", handlerMapping);

            ExtendJar firstLoad = new ExtendJar("test.reload", null, FIXTURE_PACKAGE);
            try (URLClassLoader classLoader = pluginClassLoader()) {
                registrar.register(firstLoad, classLoader);
            }
            cleaner.cleanup(firstLoad);

            ExtendJar secondLoad = new ExtendJar("test.reload", null, FIXTURE_PACKAGE);
            try (URLClassLoader classLoader = pluginClassLoader()) {
                assertThatCode(() -> registrar.register(secondLoad, classLoader))
                        .doesNotThrowAnyException();
            }
        }
    }

    private URLClassLoader pluginClassLoader() {
        URL testClasses = getClass().getProtectionDomain().getCodeSource().getLocation();
        ClassLoader filteringParent = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(FIXTURE_PACKAGE + ".")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };
        return new URLClassLoader(new URL[]{testClasses}, filteringParent);
    }
}

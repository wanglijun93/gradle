/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.cpp.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.nativeplatform.internal.Names;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultCppBinary implements CppBinary {
    private final String name;
    private final Provider<String> baseName;
    private final boolean debuggable;
    private final FileCollection sourceFiles;
    private final FileCollection includePath;
    private final FileCollection linkLibraries;
    private final FileCollection runtimeLibraries;

    public DefaultCppBinary(String name, ObjectFactory objects, Provider<String> baseName, boolean debuggable, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation) {
        this.name = name;
        this.baseName = baseName;
        this.debuggable = debuggable;
        this.sourceFiles = sourceFiles;

        Names names = Names.of(name);

        // TODO - reduce duplication with Swift binary
        Configuration includePathConfig = configurations.create(names.withPrefix("cppCompile"));
        includePathConfig.setCanBeConsumed(false);
        includePathConfig.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));
        includePathConfig.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        Configuration nativeLink = configurations.create(names.withPrefix("nativeLink"));
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_LINK));
        nativeLink.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        Configuration nativeRuntime = configurations.create(names.withPrefix("nativeRuntime"));
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.NATIVE_RUNTIME));
        nativeRuntime.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);

        includePathConfig.extendsFrom(implementation);
        nativeLink.extendsFrom(implementation);
        nativeRuntime.extendsFrom(implementation);

        includePath = componentHeaderDirs.plus(new FileCollectionAdapter(new IncludePath(includePathConfig, configurations)));
        linkLibraries = nativeLink;
        runtimeLibraries = nativeRuntime;
    }

    @Inject
    protected FileOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected TemporaryFileProvider getTemporaryFileProvider() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Provider<String> getBaseName() {
        return baseName;
    }

    @Override
    public boolean isDebuggable() {
        return debuggable;
    }

    @Override
    public FileCollection getCppSource() {
        return sourceFiles;
    }

    @Override
    public FileCollection getCompileIncludePath() {
        return includePath;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibraries;
    }

    @Override
    public FileCollection getRuntimeLibraries() {
        return runtimeLibraries;
    }

    private class IncludePath implements MinimalFileSet {
        private final Configuration includePathConfig;
        private final ConfigurationContainer configurationContainer;
        private Set<File> result;

        IncludePath(Configuration includePathConfig, ConfigurationContainer configurationContainer) {
            this.includePathConfig = includePathConfig;
            this.configurationContainer = configurationContainer;
        }

        @Override
        public Set<File> getFiles() {
            if (result == null) {
                // All this is intended to go away as more Gradle-specific metadata is included in the publications and the dependency resolution engine can just figure this stuff out for us

                // Collect up the external components in the result to resolve again to get the header zip artifact
                includePathConfig.getResolvedConfiguration().rethrowFailure();
                Set<ResolvedComponentResult> components = includePathConfig.getIncoming().getResolutionResult().getAllComponents();
                Set<ModuleComponentIdentifier> externalComponents = new HashSet<ModuleComponentIdentifier>(components.size());
                List<Dependency> externalDependencies = new ArrayList<Dependency>(components.size());
                for (ResolvedComponentResult component : components) {
                    if (component.getId() instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier id = (ModuleComponentIdentifier) component.getId();
                        externalComponents.add(id);
                        DefaultExternalModuleDependency mappedDependency = new DefaultExternalModuleDependency(id.getGroup(), id.getModule(), id.getVersion());
                        mappedDependency.addArtifact(new DefaultDependencyArtifact(id.getModule(), "zip", "zip", "cpp-api-headers", null));
                        externalDependencies.add(mappedDependency);
                    }
                }

                // Collect the files from anything other than an external component, use these directly in the result
                ArtifactCollection artifacts = includePathConfig.getIncoming().getArtifacts();
                Set<File> files = new LinkedHashSet<File>();
                for (ResolvedArtifactResult artifact : artifacts) {
                    if (!externalComponents.contains(artifact.getId().getComponentIdentifier())) {
                        files.add(artifact.getFile());
                    }
                }

                // This is intentionally dumb and will improve later
                // Download and unzip the header zip artifacts for external components. The unzipping is done on _each_ resolve.
                // Also the files of the result are not ordered as they would be if the original configuration is resolved
                if (!externalDependencies.isEmpty()) {
                    TemporaryFileProvider temporaryFileProvider = getTemporaryFileProvider();
                    FileOperations fileOperations = getFileOperations();

                    Configuration mappedConfiguration = configurationContainer.detachedConfiguration(externalDependencies.toArray(new Dependency[0]));
                    Set<File> headerZips = mappedConfiguration.getFiles();
                    for (final File zip : headerZips) {
                        final File tmpDir = temporaryFileProvider.createTemporaryDirectory("gradle-cpp-headers", null);
                        final FileTree zipTree = fileOperations.zipTree(zip);
                        final File unzipDir = new File(tmpDir, zip.getName());
                        fileOperations.copy(new Action<CopySpec>() {
                            @Override
                            public void execute(CopySpec copySpec) {
                                copySpec.from(zipTree);
                                copySpec.into(unzipDir);
                            }
                        });
                        files.add(unzipDir);
                    }
                }
                result = files;
            }
            return result;
        }

        @Override
        public String getDisplayName() {
            return "Include path for " + DefaultCppBinary.this.toString();
        }
    }
}
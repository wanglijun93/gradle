/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal.tasks;

import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Properties;

public class MavenPomFileGenerator {

    private static final String POM_FILE_ENCODING = "UTF-8";
    private static final String POM_VERSION = "4.0.0";

    private final Model model = new Model();
    private XmlTransformer xmlTransformer = new XmlTransformer();
    private final VersionRangeMapper versionRangeMapper;

    public MavenPomFileGenerator(MavenProjectIdentity identity, VersionRangeMapper versionRangeMapper) {
        this.versionRangeMapper = versionRangeMapper;
        model.setModelVersion(POM_VERSION);
        Model model = this.model;
        model.setGroupId(identity.getGroupId());
        model.setArtifactId(identity.getArtifactId());
        model.setVersion(identity.getVersion());
    }

    public MavenPomFileGenerator configureFrom(MavenPom pom) {
        model.setPackaging(pom.getPackaging());
        model.setDescription(pom.getDescription());
        model.setUrl(pom.getUrl());
        if (pom.getInceptionYear() != null) {
            model.setInceptionYear(pom.getInceptionYear().toString());
        }
        if (pom.getOrganization() != null) {
            model.setOrganization(convertOrganization(pom.getOrganization()));
        }
        if (pom.getScm() != null) {
            model.setScm(convertScm(pom.getScm()));
        }
        for (MavenPomLicense license : pom.getLicenses()) {
            model.addLicense(convertLicense(license));
        }
        for (MavenPomDeveloper developer : pom.getDevelopers()) {
            model.addDeveloper(convertDeveloper(developer));
        }
        for (MavenPomContributor contributor : pom.getContributors()) {
            model.addContributor(convertContributor(contributor));
        }
        return this;
    }

    private Organization convertOrganization(MavenPomOrganization source) {
        Organization target = new Organization();
        target.setName(source.getName());
        target.setUrl(source.getUrl());
        return target;
    }

    private License convertLicense(MavenPomLicense source) {
        License target = new License();
        target.setName(source.getName());
        target.setUrl(source.getUrl());
        return target;
    }

    private Developer convertDeveloper(MavenPomDeveloper source) {
        Developer target = new Developer();
        target.setId(source.getId());
        convertContributor(source, target);
        return target;
    }

    private Contributor convertContributor(MavenPomContributor source) {
        Contributor target = new Contributor();
        convertContributor(source, target);
        return target;
    }

    private void convertContributor(MavenPomContributor source, Contributor target) {
        target.setName(source.getName());
        target.setEmail(source.getEmail());
        target.setUrl(source.getUrl());
        target.setOrganization(source.getOrganization());
        target.setOrganizationUrl(source.getOrganizationUrl());
        target.setRoles(new ArrayList<String>(source.getRoles()));
        target.setTimezone(source.getTimezone());
        Properties properties = new Properties();
        properties.putAll(source.getProperties());
        target.setProperties(properties);
    }

    private Scm convertScm(MavenPomScm source) {
        Scm target = new Scm();
        target.setConnection(source.getConnection());
        target.setDeveloperConnection(source.getDeveloperConnection());
        target.setUrl(source.getUrl());
        target.setTag(source.getTag());
        return target;
    }

    public void addApiDependencyManagement(MavenDependency apiDependency) {
        addDependencyManagement(apiDependency, "compile");
    }

    public void addRuntimeDependencyManagement(MavenDependency dependency) {
        addDependencyManagement(dependency, "runtime");
    }

    public void addRuntimeDependency(MavenDependencyInternal dependency) {
        addDependency(dependency, "runtime");
    }

    public void addApiDependency(MavenDependencyInternal apiDependency) {
        addDependency(apiDependency, "compile");
    }

    private void addDependency(MavenDependencyInternal mavenDependency, String scope) {
        if (mavenDependency.getArtifacts().size() == 0) {
            addDependency(mavenDependency, mavenDependency.getArtifactId(), scope, null, null);
        } else {
            for (DependencyArtifact artifact : mavenDependency.getArtifacts()) {
                addDependency(mavenDependency, artifact.getName(), scope, artifact.getType(), artifact.getClassifier());
            }
        }
    }

    private void addDependency(MavenDependencyInternal dependency, String artifactId, String scope, String type, String classifier) {
        Dependency mavenDependency = new Dependency();
        mavenDependency.setGroupId(dependency.getGroupId());
        mavenDependency.setArtifactId(artifactId);
        mavenDependency.setVersion(mapToMavenSyntax(dependency.getVersion()));
        mavenDependency.setType(type);
        mavenDependency.setScope(scope);
        mavenDependency.setClassifier(classifier);

        for (ExcludeRule excludeRule : dependency.getExcludeRules()) {
            Exclusion exclusion = new Exclusion();
            exclusion.setGroupId(GUtil.elvis(excludeRule.getGroup(), "*"));
            exclusion.setArtifactId(GUtil.elvis(excludeRule.getModule(), "*"));
            mavenDependency.addExclusion(exclusion);
        }

        model.addDependency(mavenDependency);
    }

    private void addDependencyManagement(MavenDependency dependency, String scope) {
        Dependency mavenDependency = new Dependency();
        mavenDependency.setGroupId(dependency.getGroupId());
        mavenDependency.setArtifactId(dependency.getArtifactId());
        mavenDependency.setVersion(mapToMavenSyntax(dependency.getVersion()));
        mavenDependency.setScope(scope);

        DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement == null) {
            dependencyManagement = new DependencyManagement();
            model.setDependencyManagement(dependencyManagement);
        }
        dependencyManagement.addDependency(mavenDependency);
    }

    private String mapToMavenSyntax(String version) {
        return versionRangeMapper.map(version);
    }

    public MavenPomFileGenerator withXml(final Action<XmlProvider> action) {
        xmlTransformer.addAction(action);
        return this;
    }

    public MavenPomFileGenerator writeTo(File file) {
        xmlTransformer.transform(file, POM_FILE_ENCODING, new Action<Writer>() {
            public void execute(Writer writer) {
                try {
                    new MavenXpp3Writer().write(writer, model);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }
}

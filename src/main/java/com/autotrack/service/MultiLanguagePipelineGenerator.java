package com.autotrack.service;

import com.autotrack.dto.MultiLanguageCICDConfigDTO;
import com.autotrack.dto.MultiLanguageCICDConfigDTO.LanguageComponent;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating versatile multi-language CI/CD pipelines.
 */
@Service
public class MultiLanguagePipelineGenerator {
    
    /**
     * Generate a comprehensive GitHub Actions workflow for multi-language projects
     */
    public String generateWorkflow(MultiLanguageCICDConfigDTO config) {
        StringBuilder workflow = new StringBuilder();
        
        // Workflow header
        workflow.append("name: Multi-Language CI/CD Pipeline\n\n");
        
        // Triggers
        generateTriggers(workflow);
        
        // Environment variables
        generateEnvironmentVariables(workflow, config);
        
        // Jobs
        workflow.append("jobs:\n");
        
        // Generate jobs based on architecture
        switch (config.getProjectArchitecture().toLowerCase()) {
            case "monolith":
                generateMonolithJobs(workflow, config);
                break;
            case "microservices":
                generateMicroservicesJobs(workflow, config);
                break;
            case "full-stack":
            case "fullstack":
                generateFullStackJobs(workflow, config);
                break;
            case "extension":
                generateExtensionJobs(workflow, config);
                break;
            default:
                throw new IllegalArgumentException("Unknown project architecture: " + config.getProjectArchitecture());
        }
        
        return workflow.toString();
    }
    
    private void generateTriggers(StringBuilder workflow) {
        workflow.append("on:\n");
        workflow.append("  push:\n");
        workflow.append("    branches: [ main, develop ]\n");
        workflow.append("  pull_request:\n");
        workflow.append("    branches: [ main ]\n");
        workflow.append("  workflow_dispatch:\n\n");
    }
    
    private void generateEnvironmentVariables(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("env:\n");
        workflow.append("  PROJECT_NAME: ").append(config.getProjectName()).append("\n");
        workflow.append("  PROJECT_ARCHITECTURE: ").append(config.getProjectArchitecture()).append("\n");
        
        if (config.getEnvironmentVariables() != null) {
            config.getEnvironmentVariables().forEach((key, value) -> 
                workflow.append("  ").append(key).append(": ").append(value).append("\n"));
        }
        workflow.append("\n");
    }
    
    private void generateMonolithJobs(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("  build-and-test:\n");
        workflow.append("    runs-on: ubuntu-latest\n");
        workflow.append("    strategy:\n");
        workflow.append("      matrix:\n");
        workflow.append("        node-version: [18.x, 20.x]\n\n");
        
        workflow.append("    steps:\n");
        workflow.append("    - uses: actions/checkout@v4\n\n");
        
        // Setup environments for all languages
        for (LanguageComponent component : config.getComponents()) {
            generateLanguageSetup(workflow, component);
        }
        
        // Install dependencies and run tests for each component
        for (LanguageComponent component : config.getComponents()) {
            generateComponentSteps(workflow, component);
        }
        
        // Deploy if configured
        if (!config.getDeployStrategy().equals("none")) {
            generateDeploymentSteps(workflow, config);
        }
    }
    
    private void generateMicroservicesJobs(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        // Group components by language for parallel jobs
        Map<String, List<LanguageComponent>> componentsByLanguage = config.getComponents().stream()
            .collect(Collectors.groupingBy(LanguageComponent::getLanguage));
        
        componentsByLanguage.forEach((language, components) -> {
            workflow.append("  ").append(language.toLowerCase().replace("-", "_")).append("_services:\n");
            workflow.append("    runs-on: ubuntu-latest\n");
            workflow.append("    strategy:\n");
            workflow.append("      matrix:\n");
            workflow.append("        service: [").append(
                components.stream()
                    .map(c -> "\"" + (c.getDirectory() != null ? c.getDirectory() : c.getLanguage()) + "\"")
                    .collect(Collectors.joining(", "))
            ).append("]\n\n");
            
            workflow.append("    steps:\n");
            workflow.append("    - uses: actions/checkout@v4\n\n");
            
            // Setup for this language
            generateLanguageSetup(workflow, components.get(0));
            
            // Build and test each service
            workflow.append("    - name: Build and test ${{ matrix.service }}\n");
            workflow.append("      run: |\n");
            workflow.append("        cd ${{ matrix.service }}\n");
            
            for (LanguageComponent component : components) {
                if (component.getBuildCommand() != null) {
                    workflow.append("        ").append(component.getBuildCommand()).append("\n");
                }
                if (component.getTestCommand() != null) {
                    workflow.append("        ").append(component.getTestCommand()).append("\n");
                }
            }
            workflow.append("\n");
        });
        
        // Add deployment job if needed
        if (!config.getDeployStrategy().equals("none")) {
            generateDeploymentJob(workflow, config);
        }
    }
    
    private void generateFullStackJobs(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        // Separate frontend and backend components
        List<LanguageComponent> frontendComponents = config.getComponents().stream()
            .filter(c -> isFrontendLanguage(c.getLanguage()))
            .collect(Collectors.toList());
        
        List<LanguageComponent> backendComponents = config.getComponents().stream()
            .filter(c -> isBackendLanguage(c.getLanguage()))
            .collect(Collectors.toList());
        
        // Frontend job
        if (!frontendComponents.isEmpty()) {
            workflow.append("  frontend-build:\n");
            workflow.append("    runs-on: ubuntu-latest\n");
            workflow.append("    steps:\n");
            workflow.append("    - uses: actions/checkout@v4\n\n");
            
            for (LanguageComponent component : frontendComponents) {
                generateLanguageSetup(workflow, component);
                generateComponentSteps(workflow, component);
            }
        }
        
        // Backend job
        if (!backendComponents.isEmpty()) {
            workflow.append("  backend-build:\n");
            workflow.append("    runs-on: ubuntu-latest\n");
            workflow.append("    steps:\n");
            workflow.append("    - uses: actions/checkout@v4\n\n");
            
            for (LanguageComponent component : backendComponents) {
                generateLanguageSetup(workflow, component);
                generateComponentSteps(workflow, component);
            }
        }
        
        // Integration/deployment job
        if (!config.getDeployStrategy().equals("none")) {
            workflow.append("  deploy:\n");
            workflow.append("    needs: [");
            List<String> dependencies = new ArrayList<>();
            if (!frontendComponents.isEmpty()) dependencies.add("frontend-build");
            if (!backendComponents.isEmpty()) dependencies.add("backend-build");
            workflow.append(String.join(", ", dependencies));
            workflow.append("]\n");
            workflow.append("    runs-on: ubuntu-latest\n");
            workflow.append("    if: github.ref == 'refs/heads/main'\n");
            workflow.append("    steps:\n");
            workflow.append("    - uses: actions/checkout@v4\n\n");
            
            generateDeploymentSteps(workflow, config);
        }
    }
    
    private void generateExtensionJobs(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("  build-extension:\n");
        workflow.append("    runs-on: ubuntu-latest\n");
        workflow.append("    steps:\n");
        workflow.append("    - uses: actions/checkout@v4\n\n");
        
        for (LanguageComponent component : config.getComponents()) {
            generateLanguageSetup(workflow, component);
            generateComponentSteps(workflow, component);
        }
        
        // Extension-specific steps
        workflow.append("    - name: Package extension\n");
        workflow.append("      run: |\n");
        
        LanguageComponent mainComponent = config.getComponents().stream()
            .filter(LanguageComponent::isMainComponent)
            .findFirst()
            .orElse(config.getComponents().get(0));
        
        if (mainComponent.getDirectory() != null) {
            workflow.append("        cd ").append(mainComponent.getDirectory()).append("\n");
        }
        
        switch (mainComponent.getLanguage().toLowerCase()) {
            case "typescript":
            case "javascript":
                workflow.append("        vsce package\n");
                break;
            default:
                if (mainComponent.getBuildCommand() != null) {
                    workflow.append("        ").append(mainComponent.getBuildCommand()).append("\n");
                }
        }
        
        workflow.append("\n    - name: Upload extension artifact\n");
        workflow.append("      uses: actions/upload-artifact@v3\n");
        workflow.append("      with:\n");
        workflow.append("        name: extension-package\n");
        workflow.append("        path: ").append(mainComponent.getDirectory() != null ? mainComponent.getDirectory() + "/" : "").append("*.vsix\n\n");
    }
    
    private void generateDefaultJobs(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("  build:\n");
        workflow.append("    runs-on: ubuntu-latest\n");
        workflow.append("    steps:\n");
        workflow.append("    - uses: actions/checkout@v4\n\n");
        
        for (LanguageComponent component : config.getComponents()) {
            generateLanguageSetup(workflow, component);
            generateComponentSteps(workflow, component);
        }
    }
    
    private void generateLanguageSetup(StringBuilder workflow, LanguageComponent component) {
        String language = component.getLanguage().toLowerCase();
        
        switch (language) {
            case "nodejs":
            case "node_js":
            case "react":
            case "typescript":
            case "javascript":
                workflow.append("    - name: Setup Node.js\n");
                workflow.append("      uses: actions/setup-node@v4\n");
                workflow.append("      with:\n");
                workflow.append("        node-version: ").append(component.getVersion() != null ? component.getVersion() : "'20.x'").append("\n");
                workflow.append("        cache: 'npm'\n\n");
                break;
                
            case "python":
                workflow.append("    - name: Setup Python\n");
                workflow.append("      uses: actions/setup-python@v4\n");
                workflow.append("      with:\n");
                workflow.append("        python-version: ").append(component.getVersion() != null ? component.getVersion() : "'3.11'").append("\n\n");
                break;
                
            case "java":
            case "spring-boot":
                workflow.append("    - name: Setup Java\n");
                workflow.append("      uses: actions/setup-java@v4\n");
                workflow.append("      with:\n");
                workflow.append("        java-version: ").append(component.getVersion() != null ? component.getVersion() : "'17'").append("\n");
                workflow.append("        distribution: 'temurin'\n");
                workflow.append("        cache: maven\n\n");
                break;
                
            case "php":
                workflow.append("    - name: Setup PHP\n");
                workflow.append("      uses: shivammathur/setup-php@v2\n");
                workflow.append("      with:\n");
                workflow.append("        php-version: ").append(component.getVersion() != null ? component.getVersion() : "'8.2'").append("\n");
                workflow.append("        extensions: mbstring, pdo, pdo_mysql\n");
                workflow.append("        tools: composer\n\n");
                break;
        }
    }
    
    private void generateComponentSteps(StringBuilder workflow, LanguageComponent component) {
        String componentName = component.getDirectory() != null ? component.getDirectory() : component.getLanguage();
        
        workflow.append("    - name: Install dependencies for ").append(componentName).append("\n");
        workflow.append("      run: |\n");
        
        if (component.getDirectory() != null) {
            workflow.append("        cd ").append(component.getDirectory()).append("\n");
        }
        
        // Install dependencies based on language
        switch (component.getLanguage().toLowerCase()) {
            case "nodejs":
            case "react":
            case "typescript":
            case "javascript":
                workflow.append("        npm ci\n");
                break;
            case "python":
                workflow.append("        pip install -r requirements.txt\n");
                break;
            case "java":
            case "spring-boot":
                workflow.append("        mvn dependency:resolve\n");
                break;
            case "php":
                workflow.append("        composer install --no-interaction --prefer-dist\n");
                break;
        }
        
        // Run tests
        if (component.getTestCommand() != null) {
            workflow.append("\n    - name: Run tests for ").append(componentName).append("\n");
            workflow.append("      run: |\n");
            if (component.getDirectory() != null) {
                workflow.append("        cd ").append(component.getDirectory()).append("\n");
            }
            workflow.append("        ").append(component.getTestCommand()).append("\n");
        }
        
        // Build
        if (component.getBuildCommand() != null) {
            workflow.append("\n    - name: Build ").append(componentName).append("\n");
            workflow.append("      run: |\n");
            if (component.getDirectory() != null) {
                workflow.append("        cd ").append(component.getDirectory()).append("\n");
            }
            workflow.append("        ").append(component.getBuildCommand()).append("\n");
        }
        
        workflow.append("\n");
    }
    
    private void generateDeploymentSteps(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("    - name: Deploy application\n");
        workflow.append("      run: |\n");
        workflow.append("        echo \"Deploying ").append(config.getProjectName()).append(" using ").append(config.getDeployStrategy()).append(" strategy\"\n");
        
        switch (config.getDeployStrategy().toLowerCase()) {
            case "heroku":
                workflow.append("        # Add Heroku deployment commands here\n");
                workflow.append("        # heroku git:remote -a your-app-name\n");
                workflow.append("        # git push heroku main\n");
                break;
            case "vercel":
                workflow.append("        # Add Vercel deployment commands here\n");
                workflow.append("        # npx vercel --prod --token $VERCEL_TOKEN\n");
                break;
            case "docker":
                workflow.append("        # Build and push Docker image\n");
                workflow.append("        # docker build -t $PROJECT_NAME .\n");
                workflow.append("        # docker push $DOCKER_REGISTRY/$PROJECT_NAME\n");
                break;
        }
        workflow.append("\n");
    }
    
    private void generateDeploymentJob(StringBuilder workflow, MultiLanguageCICDConfigDTO config) {
        workflow.append("  deploy:\n");
        workflow.append("    needs: [");
        
        // Get all language job names
        Set<String> jobNames = config.getComponents().stream()
            .map(c -> c.getLanguage().toLowerCase().replace("-", "_") + "_services")
            .collect(Collectors.toSet());
        
        workflow.append(String.join(", ", jobNames));
        workflow.append("]\n");
        workflow.append("    runs-on: ubuntu-latest\n");
        workflow.append("    if: github.ref == 'refs/heads/main'\n");
        workflow.append("    steps:\n");
        workflow.append("    - uses: actions/checkout@v4\n\n");
        
        generateDeploymentSteps(workflow, config);
    }
    
    private boolean isFrontendLanguage(String language) {
        return Arrays.asList("react", "vue", "angular", "typescript", "javascript").contains(language.toLowerCase());
    }
    
    private boolean isBackendLanguage(String language) {
        return Arrays.asList("nodejs", "node_js", "python", "java", "spring-boot", "spring_boot", "php", "go", "rust").contains(language.toLowerCase());
    }
}

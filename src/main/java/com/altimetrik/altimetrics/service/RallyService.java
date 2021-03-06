package com.altimetrik.altimetrics.service;

import com.altimetrik.altimetrics.pojo.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.GetRequest;
import com.rallydev.rest.response.GetResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RallyService {

    private DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);

    @Autowired
    RallyRestApi rallyRestApi;

    public List<ProjectGroup> getAllProjects() throws IOException {
        GetRequest allProjectsRequest = new GetRequest("/projects");
        GetResponse allProjectsResponse = null;
        try {
            allProjectsResponse = rallyRestApi.get(allProjectsRequest);
            if (allProjectsResponse.wasSuccessful()) {
            JsonArray projectGroups = allProjectsResponse.getObject().getAsJsonObject().get("Results").getAsJsonArray();
            List<ProjectGroup> projectGroupList = new ArrayList<>();
            for (JsonElement p : projectGroups) {
                ProjectGroup group = new ProjectGroup();
                group.setGroupId(p.getAsJsonObject().get("_refObjectUUID").getAsString());
                group.setGroupName(p.getAsJsonObject().get("_refObjectName").getAsString());
                group.setProjects(getProjectGroupChildrens(group));
                //List<Iteration> iterations = getAllIterationByProject(project);
                //project.setIterations(iterations);
                if(group.getProjects().size()>0)projectGroupList.add(group);
            }

            return projectGroupList;
        }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (rallyRestApi != null) {
                rallyRestApi.close();
            }
        }
        return null;
    }

    public List<Story> getProjectCurrentIterationStories(Project project) throws IOException {
        List<Iteration> iterations = getAllIterationByProject(project);
        Optional<Iteration> currentIteration = iterations.stream().filter(i -> isCurrentIteration(i)).findFirst();
        if(currentIteration.isPresent()){
            List<Story> currentSprintStories = getStoriesBySprintId(currentIteration.get().getIterationId());
           return  currentSprintStories;
        }
        return null;
    }

    public IterationMetrics getIterationMetricsByIterationId(String iterationId) throws IOException {
        GetRequest iterationMericsRequest = new GetRequest("/iterationstatus/"+iterationId);
        GetResponse iterationMericsResponse = rallyRestApi.get(iterationMericsRequest);
        if (iterationMericsResponse.wasSuccessful()) {
            JsonObject iterationMertics = iterationMericsResponse.getObject().getAsJsonObject();
            IterationMetrics metrics = new IterationMetrics();
            metrics.setPlannedPoints(iterationMertics.get("ActualPlannedAmount").getAsInt());
            metrics.setAcceptedpoints(iterationMertics.get("AcceptedAmount").getAsInt());
            metrics.setCompletedPoints(iterationMertics.get("CompletedAmount").getAsInt());
            metrics.setDefinedPoints(iterationMertics.get("DefinedAmount").getAsInt());
            metrics.setInProgresspoints(iterationMertics.get("InProgressAmount").getAsInt());
            metrics.setDoPoints(metrics.getCompletedPoints()+metrics.getAcceptedpoints());
            return metrics;
        }
        return null;
    }

    public boolean isCurrentIteration(Iteration iteration){

        LocalDate startDate = LocalDate.parse(iteration.getStartDate(), inputFormatter);
        LocalDate endDate = LocalDate.parse(iteration.getEndDate(), inputFormatter);
        if(!LocalDate.now().isBefore(startDate) && !LocalDate.now().isAfter(endDate)){
            return true;
        }
    return false;
    }
//
//    public Project getProjectByName(String projectName) throws IOException {
//        Optional<Project> project = getAllProjects().stream().filter(p -> p.getProjectName().equalsIgnoreCase(projectName)).findFirst();
//        if (project.isPresent()) {
//            return project.get();
//        }
//        return null;
//    }

    public List<Iteration> getAllIterationByProject(Project project) throws IOException {
        GetRequest getProjectIdRequest = new GetRequest("/projects/" + project.getProjectId());
        GetResponse getProjectIdResponse = rallyRestApi.get(getProjectIdRequest);
        List<Iteration> iterationList = new ArrayList<>();
        if (getProjectIdResponse.wasSuccessful()) {
            String projectObjectId = getProjectIdResponse.getObject().getAsJsonObject().get("ObjectID").getAsString();

            GetRequest iterationListRequest = new GetRequest("/Projects/" + projectObjectId + "/Iterations");
            GetResponse iterationListResponse = rallyRestApi.get(iterationListRequest);
            if (iterationListResponse.wasSuccessful()) {
                JsonArray iterations = iterationListResponse.getObject().get("Results").getAsJsonArray();

                for (JsonElement iteration : iterations) {
                    JsonObject iterationObject = iteration.getAsJsonObject();
                    Iteration it = new Iteration();
                    it.setStartDate(iterationObject.get("StartDate").getAsString());
                    it.setEndDate(iterationObject.get("EndDate").getAsString());
                    it.setSprintName(iterationObject.get("Name").getAsString());
                    it.setIterationId(iterationObject.get("ObjectID").getAsString());
                    //it.setStories(getStoriesBySprintId(it.getIterationId()));
                    iterationList.add(it);

                }
            }
        }
        return iterationList;
    }

    public List<Story> getStoriesBySprintId(String iterationId) throws IOException {

        /// project iteration request
        GetRequest storiesListRequest = new GetRequest("/Iteration/"+iterationId+"/WorkProducts");
        GetResponse storiesListResponse = rallyRestApi.get(storiesListRequest);
        List<Story> storyList = new ArrayList<>();
        if(storiesListResponse.wasSuccessful()){
            JsonArray stories = storiesListResponse.getObject().getAsJsonObject().getAsJsonArray("Results");
            for (JsonElement story : stories) {
                JsonObject iterationObject = story.getAsJsonObject();
                Story s = new Story();
                s.setName(iterationObject.get("Name").getAsString());
                s.setPlanEstimate(iterationObject.get("PlanEstimate").toString());
                s.setScheduleState(iterationObject.get("ScheduleState").getAsString());
                s.setStoryId(iterationObject.get("FormattedID").getAsString());
                String storyType = iterationObject.get("c_StoryType")==null ?iterationObject.get("_type").getAsString():iterationObject.get("c_StoryType").getAsString();
                s.setStoryType(storyType);
                storyList.add(s);
            }
        }
        return storyList;
    }

    private List<Project> getProjectGroupChildrens(ProjectGroup projectGroup) throws IOException {
        GetRequest getChildrensRequest = new GetRequest("/Project/"+projectGroup.getGroupId()+"/Children");
        GetResponse getAllChildrensResponse = rallyRestApi.get(getChildrensRequest);
        List<Project> childrenProjectList = new ArrayList<>();
        if (getAllChildrensResponse.wasSuccessful()) {
            JsonArray projects = getAllChildrensResponse.getObject().getAsJsonObject().get("Results").getAsJsonArray();

            for (JsonElement p : projects) {
                Project newProject = new Project();
                newProject.setProjectId(p.getAsJsonObject().get("_refObjectUUID").getAsString());
                newProject.setProjectName(p.getAsJsonObject().get("_refObjectName").getAsString());
                /// to get the project description details
                GetRequest getProjectIdRequest = new GetRequest("/projects/" + projectGroup.getGroupId());
                GetResponse getProjectIdResponse = rallyRestApi.get(getProjectIdRequest);
                if(getProjectIdResponse.wasSuccessful()){
                    String description = getProjectIdResponse.getObject().getAsJsonObject().get("Description").getAsString();
                    String creationDate = getProjectIdResponse.getObject().getAsJsonObject().get("CreationDate").getAsString();
//                    System.out.println("Project ID "+ project.getProjectId());
//                    String owner = getProjectIdResponse.getObject().getAsJsonObject().get("Owner").isJsonNull() ?
//                            " ":getProjectIdResponse.getObject().getAsJsonObject().get("Owner").getAsJsonObject().get("_refObjectName").getAsString();
//                    String teamSize = getProjectIdResponse.getObject().getAsJsonObject().get("TeamMembers").isJsonNull() ?
//                            " ":getProjectIdResponse.getObject().getAsJsonObject().get("TeamMembers").getAsJsonObject().get("Count").getAsString();
                    //newProject.setDescription(description);
                   // newProject.setCreationDate(creationDate);
                    // newProject.setOwner(owner);
                    //newProject.setTeamSize(teamSize);
                }
                childrenProjectList.add(newProject);
            }
        }
        return childrenProjectList;
    }

}
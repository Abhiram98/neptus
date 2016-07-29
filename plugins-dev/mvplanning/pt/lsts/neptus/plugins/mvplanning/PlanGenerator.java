package pt.lsts.neptus.plugins.mvplanning;

import pt.lsts.imc.PlanSpecification;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.IMCUtils;
import pt.lsts.neptus.mp.ManeuverLocation;
import pt.lsts.neptus.mp.maneuvers.FollowPath;
import pt.lsts.neptus.mp.maneuvers.Goto;
import pt.lsts.neptus.mp.maneuvers.StationKeeping;
import pt.lsts.neptus.plugins.mvplanning.events.MvPlanningEventNewOpArea;
import pt.lsts.neptus.plugins.mvplanning.exceptions.BadPlanTaskException;
import pt.lsts.neptus.plugins.mvplanning.exceptions.SafePathNotFoundException;
import pt.lsts.neptus.plugins.mvplanning.interfaces.ConsoleAdapter;
import pt.lsts.neptus.plugins.mvplanning.interfaces.MapDecomposition;
import pt.lsts.neptus.plugins.mvplanning.interfaces.PlanTask;
import pt.lsts.neptus.plugins.mvplanning.interfaces.PlanTask.TASK_TYPE;
import pt.lsts.neptus.plugins.mvplanning.jaxb.profiles.Profile;
import pt.lsts.neptus.plugins.mvplanning.monitors.Environment;
import pt.lsts.neptus.plugins.mvplanning.planning.algorithm.CoverageAreaFactory;
import pt.lsts.neptus.plugins.mvplanning.planning.mapdecomposition.GridArea;
import pt.lsts.neptus.plugins.mvplanning.planning.tasks.CoverageArea;
import pt.lsts.neptus.plugins.mvplanning.planning.tasks.ToSafety;
import pt.lsts.neptus.plugins.mvplanning.planning.tasks.VisitPoint;
import pt.lsts.neptus.plugins.mvplanning.utils.MvPlanningUtils;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.mission.plan.PlanType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PlanGenerator {
    private final ReadWriteLock OP_AREA_RW_LOCK = new ReentrantReadWriteLock();
    private Thread opAreaWorker = new Thread();
    private volatile boolean opAreaCreated = false;
    /* Map decomposition needed for some algorithms, e.g, A-star */
    private static GridArea operationalArea = null;

    private ConsoleAdapter console;

    public PlanGenerator(ConsoleAdapter console) {
        this.console = console;
    }

    public void setOperationalArea(GridArea opArea) {
        OP_AREA_RW_LOCK.writeLock().lock();
        operationalArea = opArea;
        OP_AREA_RW_LOCK.writeLock().unlock();
    }

    public void computeOperationalArea(Environment env, double width, double height, double cellSize) {
        new Thread() {
            public void run() {
                OP_AREA_RW_LOCK.writeLock().lock();
                operationalArea = new GridArea(cellSize, width, height, 0, console.getMapGroup().getHomeRef().getCenterLocation(), env);
                OP_AREA_RW_LOCK.writeLock().unlock();

                NeptusLog.pub().info("Operational area [" + width +  " x " + height + "] is set. Cells are [" + cellSize + " x " + cellSize + "]");
                console.post(new MvPlanningEventNewOpArea(operationalArea));

            }
        }.start();
    }


    public void updateOperationalArea() {
        new Thread() {
            public void run() {
                OP_AREA_RW_LOCK.writeLock().lock();
                if (operationalArea != null)
                    operationalArea.updateCellsObstacles();
                OP_AREA_RW_LOCK.writeLock().unlock();
                NeptusLog.pub().info("Updated operational area");
            }
        }.start();
    }

    public void generatePlan(Profile planProfile, Object obj) {

    }

    /**
     * Given a, incomplete, PlanTask generates a plan
     * and completes it (adding it the generated PlanType).
     * If the PlanGenerator deems it necessary, it will divide
     * a plan into several others, hence returning a list of
     * PlanType.
     * */
    public List<PlanTask> generatePlan(PlanTask task) throws BadPlanTaskException, SafePathNotFoundException {
        PlanTask.TASK_TYPE type = task.getTaskType();
        List<PlanTask> plans = new ArrayList<>();

        if(type == PlanTask.TASK_TYPE.COVERAGE_AREA) {
            List<PlanType> pTypes = generateCoverageArea(task);

            if(pTypes.isEmpty())
                throw new BadPlanTaskException("No coverage area plans have been generated!");

            boolean first = true;
            for(PlanType plan : pTypes) {
                if(first)
                    task.setPlan(plan);
                else {
                    PlanTask newTask = new CoverageArea(plan.getId(), plan, task.getProfile(), ((CoverageArea) task).getDecomposition());
                    plans.add(newTask);
                }
                first = false;
            }
        }
        else if(type == PlanTask.TASK_TYPE.VISIT_POINT)
            task.setPlan(generateVisitPoint(task));
        else if(type == TASK_TYPE.SAFETY)
            task.setPlan(generateSafetyPlan((ToSafety) task));
        else
            throw new BadPlanTaskException("Unhandled task type " + type.name());

        plans.add(0, task);

        return plans;
    }

    /**
     * Given an area generate one or more plans to cover it
     * */
    private List<PlanType> generateCoverageArea(PlanTask task) {
        String id = task.getPlanId();
        Profile planProfile = task.getProfile();
        MapDecomposition areaToCover = ((CoverageArea) task).getDecomposition();

        CoverageAreaFactory covArea = new CoverageAreaFactory(id, planProfile, areaToCover, console.getMission());

        return covArea.asPlanType();
    }

    /**
     * Generate a plan to visit the given location
     * */
    private PlanType generateVisitPoint(PlanTask task) {
        /*String id = "visit_" + NameNormalizer.getRandomID();*/
        String id = task.getPlanId();
        Profile planProfile = task.getProfile();
        LocationType pointLoc = ((VisitPoint) task).getPointToVisit();

        PlanType plan = new PlanType(console.getMission());
        plan.setId(id);

        Goto point = (Goto) MvPlanningUtils.buildManeuver(planProfile, pointLoc, PlanTask.TASK_TYPE.VISIT_POINT);
        point.setId("visit_point");

        plan.getGraph().addManeuver(point);

        return plan;
    }

    private PlanType generateSafetyPlan(ToSafety task) throws SafePathNotFoundException {
        PlanType plan = new PlanType(console.getMission());
        plan.setId(task.getPlanId());

        LocationType[] locs = task.getLocations();
        FollowPath path = task.buildSafePath(computeSafePath(locs[0], locs[1]));

        plan.getGraph().addManeuver(path);
        StationKeeping sk = (StationKeeping) ToSafety.getDefaultManeuver(locs[1]);
        plan.getGraph().addManeuverAtEnd(sk);

        return plan;
    }

    /**
     * Adds a safe path from the start location to the first
     * waypoint of the given plan, and from the lsst waypoint
     * of the plan to the end location.
     * TODO Handle a safety task
     * @throws SafePathNotFoundException
     * */
    public PlanSpecification closePlan(PlanTask ptask, LocationType start, LocationType end) throws SafePathNotFoundException {
        NeptusLog.pub().info("Closing plan " + ptask.getPlanId());
        /* current plan */
        PlanType plan = ptask.asPlanType().clonePlan();
        String vehicleId = plan.getVehicle();

        /* set a vehicle by default */
        if(vehicleId == null)
            plan.setVehicle("lauv-xplore-1");


        List<ManeuverLocation> initialSafePath = computeSafePath(start, ptask.getFirstLocation());
        List<ManeuverLocation> endSafePath = computeSafePath(ptask.getLastLocation(), end);
        FollowPath initialFollowPath = new ToSafety(start, ptask.getFirstLocation(), vehicleId).buildSafePath(initialSafePath);
        FollowPath endFollowPath = new ToSafety(ptask.getLastLocation(), end, vehicleId).buildSafePath(endSafePath);

        /* set new initial maneuver */
        String currInitialManId = plan.getGraph().getInitialManeuverId();
        plan.getGraph().addManeuver(initialFollowPath);
        plan.getGraph().setInitialManeuver(initialFollowPath.id);
        plan.getGraph().addTransition(initialFollowPath.id, currInitialManId, "ManeuverIsDone");

        plan.getGraph().addManeuver(endFollowPath);
        plan.getGraph().addTransition(currInitialManId, endFollowPath.id, "ManeuverIsDone");

        StationKeeping sk = (StationKeeping) ToSafety.getDefaultManeuver(end);
        plan.getGraph().addManeuverAtEnd(sk);

        return (PlanSpecification) IMCUtils.generatePlanSpecification(plan);
    }

    private List<ManeuverLocation> computeSafePath(LocationType start, LocationType end) throws SafePathNotFoundException {
        OP_AREA_RW_LOCK.readLock().lock();
        List<ManeuverLocation> safePath = operationalArea.getShortestPath(start, end);
        OP_AREA_RW_LOCK.readLock().unlock();

        return safePath;
    }
}
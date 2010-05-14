package rails.algorithms;

import java.awt.EventQueue;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleGraph;

import rails.game.GameManagerI;
import rails.game.MapHex;
import rails.game.PhaseI;
import rails.game.PublicCompanyI;
import rails.game.TrainI;
import rails.game.TrainTypeI;
import rails.ui.swing.hexmap.HexMap;


public final class RevenueAdapter implements Runnable {

    protected static Logger log =
        Logger.getLogger(RevenueAdapter.class.getPackage().getName());
    
    // define VertexVisitSet
    public class VertexVisit {
        Set<NetworkVertex> set;
        VertexVisit() {set = new HashSet<NetworkVertex>();}
        public String toString() {
            return "VertexVisit Set:" + set;
        }
    }
    
    // basic links, to be defined at creation
    private final GameManagerI gameManager;
    private final NetworkGraphBuilder graphBuilder;
    private final PublicCompanyI company;
    private final PhaseI phase;

    // basic components, defined empty at creation
    private SimpleGraph<NetworkVertex, NetworkEdge> graph;
    private Set<NetworkVertex> startVertices;
    private List<NetworkTrain> trains;
    private List<VertexVisit> vertexVisitSets;
    private List<RevenueBonus> revenueBonuses;
    private Set<NetworkVertex> protectedVertices;
    
    // components related to the revenue calculator
    private RevenueCalculator rc;
    SimpleGraph<NetworkVertex,NetworkEdge> rcGraph;
    private List<NetworkVertex> rcVertices;
    private List<NetworkEdge> rcEdges;
    private List<RevenueTrainRun> optimalRun;
    
    // revenue listener to communicate results
    private RevenueListener revenueListener;
    
    public RevenueAdapter(GameManagerI gameManager, NetworkGraphBuilder graphBuilder, PublicCompanyI company, PhaseI phase){
        this.gameManager = gameManager;
        this.graphBuilder = graphBuilder;
        this.company = company;
        this.phase = phase;
        
        this.graph = new SimpleGraph<NetworkVertex, NetworkEdge>(NetworkEdge.class);
        this.trains = new ArrayList<NetworkTrain>();
        this.startVertices = new HashSet<NetworkVertex>();
        this.vertexVisitSets = new ArrayList<VertexVisit>();
        this.revenueBonuses = new ArrayList<RevenueBonus>();
        this.protectedVertices = new HashSet<NetworkVertex>();
    }
    
    public PhaseI getPhase() {
        return phase;
    }
    
    public Set<NetworkVertex> getVertices() {
        return graph.vertexSet();
    }
    
    public Set<NetworkEdge> getEdges() {
        return graph.edgeSet();
    }
    
    public SimpleGraph<NetworkVertex,NetworkEdge> getRCGraph() {
        return rcGraph;
    }
    
    public int getRCVertexId(NetworkVertex vertex) {
        return rcVertices.indexOf(vertex);
    }

    public int getRCEdgeId(NetworkEdge edge) {
        return rcEdges.indexOf(edge);
    }
        
    public Set<NetworkVertex> getStartVertices() {
        return startVertices;
    }
    
    public boolean addStartVertex(NetworkVertex startVertex) {
        if (startVertices.contains(startVertex)) return false;
        
        startVertices.add(startVertex);
        return true;
    }
    
    public List<NetworkTrain> getTrains() {
        return trains;
    }
    
    public boolean addTrain(TrainI railsTrain){
        NetworkTrain train = NetworkTrain.createFromRailsTrain(railsTrain);
        if (train == null) {
            return false;
        } else {
            trains.add(train);
            return true;
        }
    }

    public boolean addTrainByString(String trainString) {
        TrainTypeI trainType = gameManager.getTrainManager().getTypeByName(trainString.trim());
        if (trainType != null) { // string defines available trainType
            log.info("RA: found trainType" + trainType);
            TrainI railsTrain = trainType.cloneTrain();
            return addTrain(railsTrain);
        } else { // otherwise interpret the train
            NetworkTrain train = NetworkTrain.createFromString(trainString);
            if (train == null) return false;
            trains.add(train);
            return true;
        }
    }

    public List<VertexVisit> getVertexVisitSets() {
        return vertexVisitSets;
    }
    
    public void addVertexVisitSet(VertexVisit visit) {
        vertexVisitSets.add(visit);
        protectedVertices.addAll(visit.set);
    }
    
    public List<RevenueBonus> getRevenueBonuses() {
        return revenueBonuses;
    }
    
    public void addRevenueBonus(RevenueBonus bonus)  {
        revenueBonuses.add(bonus);
        protectedVertices.addAll(bonus.getVertices());
    }
    
    public void populateFromRails() {
        // define graph, without HQ
        graph = graphBuilder.getRailRoadGraph(company, false);
        
        // define startVertexes
        startVertices.addAll(graphBuilder.getCompanyBaseTokenVertexes(company));
        
        // define visit sets
        defineVertexVisitSets();
        
        // define revenueBonuses
        defineRevenueBonuses();
        
        // define Trains
        company.getPortfolio().getTrainList();
        for (TrainI train:company.getPortfolio().getTrainList()) {
            addTrain(train);
        }

    }
    private void defineVertexVisitSets() {
        // define map of all locationNames 
        Map<String, VertexVisit> locations = new HashMap<String, VertexVisit>();
        for (NetworkVertex vertex:getVertices()) {
            String ln = vertex.getCityName();
            if (ln == null) continue;
            if (locations.containsKey(ln)) {
                locations.get(ln).set.add(vertex);
            } else {
                VertexVisit v = new VertexVisit();
                v.set.add(vertex);
                locations.put(ln, v);
            }
        }
        log.info("Locations = " + locations);
        // convert the location map to the vertex sets
        for (VertexVisit location:locations.values()) {
            if (location.set.size() >= 2) {
                addVertexVisitSet(location);
            }
        }
    }

    private void defineRevenueBonuses() {
        // create set of all hexes
        Set<MapHex> hexes = new HashSet<MapHex>();
        for (NetworkVertex vertex:getVertices()) {
            MapHex hex = vertex.getHex();
            if (hex != null) hexes.add(hex);
        }
        
        // check each vertex hex for a potential revenue bonus
        for (MapHex hex:hexes) {
            
            List<RevenueBonusTemplate> bonuses = new ArrayList<RevenueBonusTemplate>();
            List<RevenueBonusTemplate> hexBonuses = hex.getRevenueBonuses();
            if (hexBonuses != null) bonuses.addAll(hexBonuses);
            List<RevenueBonusTemplate> tileBonuses = hex.getCurrentTile().getRevenueBonuses();
            if (tileBonuses != null) bonuses.addAll(tileBonuses);

            if (bonuses == null) continue;
            for (RevenueBonusTemplate bonus:bonuses) {
                addRevenueBonus(bonus.toRevenueBonus(hex, gameManager, graphBuilder));
            }
        }
        log.info("RA: RevenueBonuses = " + revenueBonuses);
    }

    
    public void initRevenueCalculator(){
        // optimize graph (optimizeGraph clones the graph)
        rcGraph = NetworkGraphBuilder.optimizeGraph(graph, protectedVertices);
        
        // define the vertices and edges lists
        rcVertices = new ArrayList<NetworkVertex>(rcGraph.vertexSet());
        NetworkVertex.initAllRailsVertices(rcVertices, company, phase);
        // define ordering on vertexes by value
        Collections.sort(rcVertices, new NetworkVertex.ValueOrder());
        rcEdges = new ArrayList<NetworkEdge>(rcGraph.edgeSet());

        // prepare train length
        prepareTrainLengths(rcVertices);

        // check dimensions
        int maxVisitVertices = maxVisitVertices();
        int maxBonusVertices = maxRevenueBonusVertices();
        int maxNeighbors = maxVertexNeighbors(rcVertices);
         
        rc = new RevenueCalculator(this, rcVertices.size(), rcEdges.size(), 
                    maxNeighbors, maxVisitVertices, trains.size(), maxBonusVertices); 
        
        populateRevenueCalculator();
    }

    private int maxVisitVertices() {
        int maxNbVertices = 0;
        for (VertexVisit vertexVisit:vertexVisitSets) {
            maxNbVertices = Math.max(maxNbVertices, vertexVisit.set.size());
        }
        log.info("RA: Block of " + vertexVisitSets + ", maximum vertices in a set = "+ maxNbVertices);
        return maxNbVertices;
    }


    private int maxVertexNeighbors(Collection<NetworkVertex> vertices) {
        int maxNeighbors = 0;
        for (NetworkVertex vertex:vertices) {
            maxNeighbors = Math.max(maxNeighbors, rcGraph.edgesOf(vertex).size());
        }
        log.info("RA: Maximum neighbors in graph = "+ maxNeighbors);
        return maxNeighbors;
    }

    private int maxRevenueBonusVertices() {
        // get the number of non-simple bonuses
        int nbBonuses = RevenueBonus.getNumberNonSimpleBonuses(revenueBonuses);
        log.info("Number of non simple bonuses = " + nbBonuses);
        return nbBonuses;
    }
    
    private void prepareTrainLengths(Collection<NetworkVertex> vertices) {
        
        // separate vertexes
        List<NetworkVertex> cities = new ArrayList<NetworkVertex>();
        List<NetworkVertex> towns = new ArrayList<NetworkVertex>();
        for (NetworkVertex vertex: vertices) {
            if (vertex.isMajor()) cities.add(vertex);
            if (vertex.isMinor()) towns.add(vertex);
        }
        
        int maxCities = cities.size();
        int maxTowns = towns.size();
        
        // check train lengths
        int maxCityLength = 0, maxTownLength = 0;
        for (NetworkTrain train: trains) {
            int trainTowns = train.getMinors();
            if (train.getMajors() > maxCities) {
                trainTowns = trainTowns+ train.getMajors() - maxCities;
                train.setMajors(maxCities);
            }
            train.setMinors(Math.min(trainTowns, maxTowns));

            maxCityLength = Math.max(maxCityLength, train.getMajors());
            maxTownLength = Math.max(maxTownLength, train.getMinors());
        }
        
    }
    
    private void populateRevenueCalculator(){
        
        for (int id=0; id < rcVertices.size(); id++){ 
            NetworkVertex v = rcVertices.get(id);
            // add to revenue calculator
            v.addToRevenueCalculator(rc, id);
            for (int trainId=0; trainId < trains.size(); trainId++) {
                NetworkTrain train = trains.get(trainId);
                rc.setVertexValue(id, trainId, getVertexValue(v, train, phase));
            }
            
            // set neighbors
            if (!v.isSink()) {
                List<NetworkVertex> neighbors = Graphs.neighborListOf(rcGraph, v); 
                int j=0, neighborsArray[] = new int[neighbors.size()];
                for (NetworkVertex n:neighbors){
                    neighborsArray[j++] = rcVertices.indexOf(n);
                }
                // sort by value orderboolean activatePrediction
                Arrays.sort(neighborsArray, 0, j);
                // define according edges
                int[] edgesArray = new int[j];
                for (int e=0; e < j; e++) {
                    NetworkVertex n = rcVertices.get(neighborsArray[e]);
                    edgesArray[e] = rcEdges.indexOf(rcGraph.getEdge(v, n));
                }
                rc.setVertexNeighbors(id, neighborsArray, edgesArray);
            }
        }

        // set startVertexes
        int startVertexId =0, sv[] = new int[startVertices.size()];
        for (NetworkVertex startVertex:startVertices) {
            sv[startVertexId++] = rcVertices.indexOf(startVertex);
        }
        Arrays.sort(sv); // sort by value order 
        rc.setStartVertexes(sv);
        
        // set edges
        for (int id=0; id < rcEdges.size(); id++) {
            // prepare values
            NetworkEdge e = rcEdges.get(id);
            boolean greedy = e.isGreedy();
            int distance = e.getDistance();
            rc.setEdge(id, greedy, distance);
        }
        
        // set trains
        for (int id=0; id < trains.size(); id++) {
            NetworkTrain train = trains.get(id);
            train.addToRevenueCalculator(rc, id);
        }
        
        // set vertex sets
        for (VertexVisit visit:vertexVisitSets) {
            int j=0, setArray[] = new int[visit.set.size()];
            for (NetworkVertex n:visit.set){
                setArray[j++] = rcVertices.indexOf(n);
            }
            rc.setVisitSet(setArray);
        }
        
        
        log.info("rcVertices:" + rcVertices);
        // set revenue bonuses
        int id = 0;
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.addToRevenueCalculator(rc, id, rcVertices, trains, phase)) id ++;
        }
        
    }

    public int getVertexValue(NetworkVertex vertex, NetworkTrain train, PhaseI phase) {
        
        // base value
        int value = vertex.getValueByTrain(train);
        
        // add potential revenueBonuses
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.checkSimpleBonus(vertex, train.getRailsTrainType(), phase)) {
                value += bonus.getValue();
            }
        }
        
        return value;
    }
    
    public String getVertexValueAsString(NetworkVertex vertex, NetworkTrain train, PhaseI phase) {
        StringBuffer s = new StringBuffer();
        
        // base value
        s.append(vertex.getValueByTrain(train));
        
        // add potential revenueBonuses
        for (RevenueBonus bonus:revenueBonuses) {
            if (bonus.checkSimpleBonus(vertex, train.getRailsTrainType(), phase)) {
                s.append("+" + bonus.getValue());
            }
        }
        return s.toString();
    }
    
    
    private List<RevenueTrainRun> convertRcRun(int[][] rcRun) {
        
        List<RevenueTrainRun> convertRun = new ArrayList<RevenueTrainRun>(); 
        for (int j=0; j < rcRun.length; j++) {
            RevenueTrainRun trainRun = new RevenueTrainRun(this, trains.get(j));
            for (int v=0; v < rcRun[j].length; v++) {
                int vertexId = rcRun[j][v];
                if (vertexId == -1) break;
                trainRun.addVertex(rcVertices.get(vertexId));
            }
            convertRun.add(trainRun);
        }
        return convertRun;
    }
    
    public int calculateRevenue() {
        return calculateRevenue(0, trains.size() - 1);
    }
    
    public int calculateRevenue(int startTrain, int finalTrain) {
        if (startTrain < 0 || finalTrain >= trains.size() || startTrain > finalTrain) {
            optimalRun = null;
            return 0;
        }
        rc.initialPredictionRuns(startTrain, finalTrain);
        int value = rc.calculateRevenue(startTrain, finalTrain);
        optimalRun = convertRcRun(rc.getOptimalRun());
        return value;
    }
    public  List<RevenueTrainRun> getOptimalRun() {
        return optimalRun;
    }

    public void addRevenueListener(RevenueListener listener) {
        this.revenueListener = listener;
    }

    void notifyRevenueListener(final int revenue, final boolean finalResult) {
        if (revenueListener == null) return;
        
        EventQueue.invokeLater(
                new Runnable() {
                    public void run() {
                        revenueListener.revenueUpdate(revenue, finalResult);
                    }
                });
    }
    
    public void run() {
        calculateRevenue(0, trains.size() -1);
    }
    
    public void removeRevenueListener() {
        // only removes revenueListener
        revenueListener = null;
    }
    

    public String getOptimalRunPrettyPrint() {
        if (optimalRun == null) return "No Optimal Run";

        StringBuffer runPrettyPrint = new StringBuffer();
        for (RevenueTrainRun run:optimalRun) {
            runPrettyPrint.append(run.prettyPrint());
        }
        return runPrettyPrint.toString();
    }
    
    public void drawOptimalRunAsPath(HexMap map) {
        List<GeneralPath> pathList = new ArrayList<GeneralPath>();
        if (optimalRun != null) {
            for (RevenueTrainRun run:optimalRun) {
                pathList.add(run.getAsPath(map));
            }
        }
        map.setTrainPaths(pathList);
    }
    
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("RevenueCalculator:\n" + rc + "\n");
        buffer.append("rcVertices:\n" + rcVertices + "\n");
        buffer.append("rcEdges:\n" + rcEdges + "\n");
        buffer.append("startVertices:" + startVertices);
        return buffer.toString();
    }
    
}

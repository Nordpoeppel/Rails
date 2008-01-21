/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/TrainManager.java,v 1.13 2008/01/21 22:57:29 evos Exp $ */package rails.game;import java.util.*;import rails.game.state.IntegerState;import rails.util.LocalText;import rails.util.Tag;public class TrainManager implements TrainManagerI, ConfigurableComponentI{    // Static attributes	protected List<TrainTypeI> lTrainTypes		= new ArrayList<TrainTypeI>();	protected Map<String, TrainTypeI> mTrainTypes 		= new HashMap<String, TrainTypeI>();		protected boolean buyAtFaceValueBetweenDifferentPresidents = false;	// Dynamic attributes	protected Portfolio unavailable = null;	protected IntegerState newTypeIndex;	protected boolean trainsHaveRusted = false;	protected boolean phaseHasChanged = false;	protected boolean trainAvailabilityChanged = false;	protected List<PublicCompanyI> companiesWithExcessTrains;	// Non-game attributes    private static TrainManagerI instance = null;    protected Portfolio ipo = null;    	/**	 * No-args constructor.	 */	public TrainManager()	{		instance = this;		ipo = Bank.getIpo();		unavailable = Bank.getUnavailable();		// Nothing to do here, everything happens when configured.                newTypeIndex = new IntegerState ("NewTrainTypeIndex", 0);	}	public static TrainManagerI get()	{		return instance;	}	/**	 * @see rails.game.ConfigurableComponentI#configureFromXML(org.w3c.dom.Element)	 */	public void configureFromXML(Tag tag) throws ConfigurationException	{		TrainType defaultType = null;		TrainType newType;		Tag defaultsTag = tag.getChild("Defaults");		if (defaultsTag != null)		{			defaultType = new TrainType(false);			defaultType.configureFromXML(defaultsTag);		}		List<Tag> typeTags = tag.getChildren("Train");		for (Tag typeTag: typeTags)		{			if (defaultType != null)			{				newType = (TrainType) defaultType.clone();				if (newType == null) {				    throw new ConfigurationException (				            "Cannot clone traintype " + defaultType.getName());				}			}			else			{				newType = new TrainType(true);			}			lTrainTypes.add(newType);			newType.configureFromXML(typeTag);			mTrainTypes.put(newType.getName(), newType);		}		// Special train buying rules		Tag rulesTag = tag.getChild("TrainBuyingRules");		if (rulesTag != null) {		    // A 1851 special		    buyAtFaceValueBetweenDifferentPresidents =		        rulesTag.getChild("FaceValueIfDifferentPresidents") != null;		}		// Finish initialisation of the train types		for (TrainTypeI type : lTrainTypes)		{			if (type.getReleasedTrainTypeName() != null)			{				type.setReleasedTrainType(mTrainTypes.get(type.getReleasedTrainTypeName()));			}			if (type.getRustedTrainTypeName() != null)			{				type.setRustedTrainType(mTrainTypes.get(type.getRustedTrainTypeName()));			}		}		// By default, set the first train type to "available".		newTypeIndex.set(0);		lTrainTypes.get(newTypeIndex.intValue()).setAvailable();	}	/**	 * Make an abbreviated list of trains, like "2(6) 3(5)" etc, to show in the	 * IPO.	 * 	 * @param holder	 *            The Portfolio for which this list will be made (always IPO).	 */	public static String makeAbbreviatedList(Portfolio holder)	{		StringBuffer b = new StringBuffer();		TrainI[] trains;		for (TrainTypeI type : instance.getTrainTypes())		{			trains = holder.getTrainsPerType(type);			if (trains.length > 0)			{				if (b.length() > 0)					b.append(" ");				b.append(type.getName()).append("(");				if (type.hasInfiniteAmount())				{					b.append("+");				}				else				{					b.append(trains.length);				}				b.append(")");			}		}		return b.toString();	}	/**	 * Make a full list of trains, like "2 2 3 3", to show in any field	 * describing train possessions, except the IPO.	 * 	 * @param holder	 *            The Portfolio for which this list will be made.	 */	public static String makeFullList(Portfolio holder)	{		List<TrainI> trains = holder.getTrainList();		if (trains == null || trains.size() == 0)			return "";		return makeFullList(trains);	}	public static String makeFullList(List<TrainI> trains)	{		StringBuffer b = new StringBuffer();		for (TrainI train : trains)		{			if (b.length() > 0)				b.append(" ");            if (train.isObsolete()) b.append("(");			b.append(train.toDisplay());            if (train.isObsolete()) b.append(")");		}		return b.toString();	}	/**	 * This method handles any consequences of new train buying (from the IPO),	 * such as rusting and phase changes. It must be called <b>after</b> the	 * train has been transferred.	 * 	 */	public void checkTrainAvailability(TrainI train, Portfolio from)	{		trainsHaveRusted = false;		phaseHasChanged = false;		if (from != ipo)			return;		TrainTypeI boughtType, nextType;		boughtType = train.getType();		if (boughtType == ((TrainTypeI) lTrainTypes.get(newTypeIndex.intValue()))				&& ipo.getTrainOfType(boughtType) == null)		{			// Last train bought, make a new type available.            newTypeIndex.add(1);			nextType = ((TrainTypeI) lTrainTypes.get(newTypeIndex.intValue()));			if (nextType != null)			{				if (!nextType.isAvailable())					nextType.setAvailable();				trainAvailabilityChanged = true;				ReportBuffer.add("All " + boughtType.getName()						+ "-trains are sold out, " + nextType.getName()						+ "-trains now available");			}		}		if (boughtType.getNumberBoughtFromIPO() == 1)		{			// First train of a new type bought			ReportBuffer.add(LocalText.getText("FirstTrainBought", boughtType.getName()));			String newPhase = boughtType.getStartedPhaseName();			if (newPhase != null)			{				PhaseManager.getInstance().setPhase(newPhase);				phaseHasChanged = true;			}			TrainTypeI rustedType = boughtType.getRustedTrainType();			if (rustedType != null && !rustedType.hasRusted())			{				rustedType.setRusted(train.getHolder()); // Or obsolete, where applicable				ReportBuffer.add(LocalText.getText("TrainsRusted", rustedType.getName()));				trainsHaveRusted = true;				trainAvailabilityChanged = true;			}			TrainTypeI releasedType = boughtType.getReleasedTrainType();			if (releasedType != null)			{				if (!releasedType.isAvailable())					releasedType.setAvailable();				ReportBuffer.add(LocalText.getText("TrainsAvailable", releasedType.getName()));				trainAvailabilityChanged = true;			}		}	}	public List<TrainI> getAvailableNewTrains()	{		List<TrainI> availableTrains = new ArrayList<TrainI>();		TrainI train;		for (TrainTypeI type : lTrainTypes)		{			if (type.isAvailable())			{				train = ipo.getTrainOfType(type);				if (train != null)				{					availableTrains.add(train);				}			}		}		return availableTrains;	}	public TrainTypeI getTypeByName(String name)	{		return (TrainTypeI) mTrainTypes.get(name);	}	public List<TrainTypeI> getTrainTypes()	{		return lTrainTypes;	}	public boolean hasAvailabilityChanged()	{		return trainAvailabilityChanged;	}	public void resetAvailabilityChanged()	{		trainAvailabilityChanged = false;	}	public boolean hasPhaseChanged()	{		return phaseHasChanged;	}		public boolean buyAtFaceValueBetweenDifferentPresidents() {	    return buyAtFaceValueBetweenDifferentPresidents;	}}
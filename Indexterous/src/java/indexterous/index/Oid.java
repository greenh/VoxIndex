package indexterous.index;

import org.bson.types.ObjectId;

public class Oid {

	public synchronized static ObjectId oid() {
		return new ObjectId();
	}
}

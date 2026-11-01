import java.util.ArrayList;
import java.util.Map;

public class Record
{
    // Default non-initialized values to check
    public static final int DEFAULTID = -1;
    public static final boolean DEFAULTSTATE = false;
    public static final String DEFAULTNAME = "";
    public static final double DEFAULTPRICE = 0f;

    // Here is all stuff, according to object sphere description :3
    // id : int -> int
    // is_available : bool -> boolean
    // name : char(60) -> byte[] | String
    // price : double

    private int id;
    private boolean isAvailable;
    private String name;
    private double price;

    // Creates empty Record, which is used for marking empty strings
    public Record()
    {
        this.id = DEFAULTID;
        this.isAvailable = DEFAULTSTATE;
        this.name = DEFAULTNAME;
        this.price = DEFAULTPRICE;
    }

    // Default contructor
    public Record(int id, boolean isAvailable, String name, double price)
    {
        this.id = id;
        this.isAvailable = isAvailable;
        this.name = name;
        this.price = price;

        validateRecord();
    }

    // ArrayList Constructor
    public Record(ArrayList<Object> list)
    {
        if (list.size() == 4)
        {
            this.id = (Integer) list.get(0);
            this.isAvailable = (Boolean) list.get(1);
            this.name = (String) list.get(2);
            this.price = (Double) list.get(3);
            validateRecord();
        }
        else
        {
            throw new IllegalArgumentException("Too few values to unpack.");
        }
    }

    // Record Map Constructor
    public Record(Map<String, Object> map)
    {
        this.id = (Integer) map.getOrDefault("id", -1);
        this.isAvailable = (Boolean) map.getOrDefault("isAvailable", false);
        this.name = (String) map.getOrDefault("name", "NONE");
        this.price = (Double) map.getOrDefault("price", 0f);
        validateRecord();
    }

    public void validateRecord()
    {
        this.price = (this.price > 0f) ? price : 0;
        this.name = (this.name != null && (!this.name.isBlank() || this.name.length() > 60)) ? name : "NONE";
        this.id = (this.id >= -1) ? id : -1;
    }
    
    public boolean isValid()
    {
        return !(this.price == 0f || "NONE".equals(this.name) || this.id == -1 || this.name.isBlank());
    }

    // Getters
    public int getID() { return this.id; }
    public boolean isAvailable() { return this.isAvailable; }
    public String getName() { return this.name; }
    public double getPrice() { return this.price; }

    @Override
    public String toString() {
        return "Record{" +
                "id=" + id +
                ", isAvailable=" + isAvailable +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }
}
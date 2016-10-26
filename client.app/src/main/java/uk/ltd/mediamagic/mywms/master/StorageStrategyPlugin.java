package uk.ltd.mediamagic.mywms.master;

import java.util.Arrays;
import java.util.List;

import de.linogistix.los.inventory.model.LOSStorageStrategy;
import uk.ltd.mediamagic.flow.crud.BODTOPlugin;
import uk.ltd.mediamagic.flow.crud.SubForm;
import uk.ltd.mediamagic.mywms.common.MyWMSUserPermissions;

@SubForm(title="Main", properties={"name", "useItemZone", "zone", "useStorage", "usePicking", "mixItem", "mixClient", "clientMode", "orderByMode"})
public class StorageStrategyPlugin extends BODTOPlugin<LOSStorageStrategy> {
	
	public StorageStrategyPlugin() {
		super(LOSStorageStrategy.class);
		setUserPermissions(new MyWMSUserPermissions.ForMasterData());
	}
	
	@Override
	public String getPath() {
		return "{1, _Master Data} -> {1, _Strategies} -> {1, _Storage Strategy}";
	}

	@Override
	protected List<String> getTableColumns() {
		return Arrays.asList("id", "name");
	}

}
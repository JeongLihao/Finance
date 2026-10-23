package finance.gui;

import finance.block.entity.CompanyMachineBlockEntity;

/** 公司机器菜单（加工机、采集机）给界面共用的部分：机器本身、和公司仓库的连接、档次。 */
public interface MachineMenu {

    CompanyMachineBlockEntity blockEntity();

    /** {@link CompanyMachineBlockEntity#PERSONAL} / COMPANY / LINKED。 */
    int link();

    /** 档次 1～3。 */
    int tier();
}

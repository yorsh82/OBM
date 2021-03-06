<script language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - Language : Tiếng Việt                                               //
//     - File     : projet.inc                                               //
///////////////////////////////////////////////////////////////////////////////
// $Id$ //
///////////////////////////////////////////////////////////////////////////////

$l_tasktype = "Thể loại";
$l_task = "Nhiệm vụ";
$l_soldtime = "Thời gian bán hàng";
$l_estimatedtime = "Thời gian ước tính";
$l_shortname = "Tên ngắn";
$l_sold = "Đã bán";
$l_estimated = "Ước tính";
$l_allotted = "Phân bổ";
$l_projected = "Dự kiến";
$l_used = "Đã dùng";
$l_missing = "Đang thiếu";
$l_alloestimated = "Ước tính phân bổ thời gian";
$l_usedestimated = "Ước lượng thời gian sử dụng";
$l_progress = "Tiến độ dự án";
$l_progeval = "Đánh giá Trước/Trễ";
$l_parent = "Nhiệm vụ cha";
$l_soldtime = "Thời gian bán";
$l_projtime = "Thời gian lập kế hoạch";
$l_total = "Tổng";
$l_resume = "Tóm tắt";
$l_tasks = "Nhiệm vụ";
$l_reftask_manage = "Quản lý công việc tham chiếu";
$l_delete_reftask = "Bạn sắp xóa một nhiệm vụ tham chiếu, bạn có muốn tiếp tục?"; 
$l_reftask = 'Nhiệm vụ tham chiếu';
$l_ref = "Tham chiếu";
$l_project_date = "Ngày";
$l_project_duration = "Thời hạn";
$l_project_desc = "Mô tả";
$l_project_tech = "Mô tả kỹ thuật";
$l_closing = 'Hàng rào';
$l_closing_no = 'Dự án này không có hàng rào';
$l_members_info = "Thành viên dự án";
$l_progressinfos = "Đánh giá tiến độ: thời gian (sử dụng / mất / dự kiến)";
$l_memberstimeinfos = "Điền ô thời gian làm việc (ngày) dự kiến ​​cho từng người được giao một nhiệm vụ";
$l_fill_advance_form = "Điền ô thời gian làm việc : dự kiến / còn lại";
$l_task_new = "Nhiệm vụ mới";
$l_task_update = "Cập nhật nhiệm vụ";
$l_task_list = "Nhiệm vụ đã có";
$l_progress_info = "Điền ô thời gian làm việc (ngày) đang thiếu cho mỗi người để hoàn thành nhiệm vụ";
$l_select_project = "Chọn một dự án";
$l_mail_project_subject = "OBM : Dự án";
$l_mail_project_member = "Bạn đã được giao tham gia dự án:";
$l_insert = "Tạo dự án";
$l_update = "Cập nhật dự án";
$l_delete = "Xóa dự án"; 
$l_progress_update = "Cập nhật tiến độ dự án";
$l_task_add = "Thêm nhiệm vụ"; 
$l_task_upd = "Cập nhật nhiệm vụ";
$l_task_del = "Xóa nhiệm vụ";
$l_closing_insert = "Lưu hàng rào";
$l_closing_update = "Cập nhật hàng rào";
$l_closing_delete = "Xóa hàng rào";
$l_no_display = "Điền vào các trường tìm kiếm và nhấn <Tìm> để lấy các dự án.";
$l_found = "Các dự án đã tìm thấy.";
$l_no_found = "Không có dự án phù hợp với truy vấn đã được tìm thấy!"; 
$l_no_tasks = "Không có nhiệm vụ xác định";
$l_no_members = "Không có nhiệm vụ xác định";
$l_no_allocation = "Các thành viên của dự án đã không được giao một nhiệm vụ nào";
$l_link_timetask = "Dự án được sử dụng trong quản lý thời gian";
$l_link_timetask_no = "Dự án không được sử dụng trong quản lý thời gian";
$l_cant_delete = "Dự án không thể xóa!"; 
$l_can_delete = "Dự án có thể bị xóa.";
$l_no_task_del = "Không có nhiệm vụ để loại bỏ";
$l_ptask_same_task = "Nhiệm vụ cha không thể giống như nhiệm vụ này!"; 
$l_ptask_and_parent = "Một nhiệm vụ cha không thể có cha!"; 
$l_ptask_has_parent = "Nhiệm vụ cha bây giờ có cha!";
$l_ptask_has_user = "Nhiệm vụ cha đã được gán cho người dùng!"; 
$l_del_member_sel = "Xóa những người dùng này";
$l_member_added = "Đã thêm thành viên";
$l_member_removed = "Đã xóa thành viên";
$l_no_member_add = "Không có thành viên nào để thêm";
$l_no_member_del = "Không có thành viên nào để xóa";
$l_add_member = "Thêm thành viên vào dự án";
$l_board_no_data = "Bảng điều khiển không thể hiển thị nếu thiếu công việc hoặc các thành viên!"; 
$l_select_task = "Bạn phải chọn một nhiệm vụ";
$l_fill_name = "Bạn phải điền tên trước khi gửi.";
$l_fill_tt = "Bạn phải điền loại trước khi gửi."; 
$l_fill_soldtime = "Bạn phải điền thời gian bán trước khi gửi.";
$l_fill_estimated = "Bạn phải điền thời gian ước tính trước khi gửi.";
$l_fill_tasklabel = "Bạn phải điền tên cho nhiệm vụ này."; 
$l_fill_soldnumber = "Lượng thời gian bán không hợp lệ.";
$l_fill_projected = "Mỗi thành viên phải được giao một nhiệm vụ";
$l_fill_projnumber = "Số lượng thời gian dự kiến không hợp lệ.";
$l_fill_missing = "Thời gian thiếu phải được điền cho tất cả các thành viên";
$l_fill_missnumber = "Số lượng thời gian thiếu không hợp lệ.";
$l_tolong_shortname = "Tên viết tắt quá dài";
$l_err_used = "Thời gian sử dụng không hợp lệ!";
$l_err_remaining = "Thời gian còn lại không hợp lệ!";
$l_delete_confirm = "Xác nhận việc xóa dự án?";
$l_project_display = "Hiển thị tùy chọn cho danh sách các dự án";

